package com.paytech.platform.support.integration.domainservice.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 验证用例输入模板（沙箱验证技术方案第 4 节）：字符串值中的 ${...} 变量。整串只有一个变量时保留原类型，
 * 嵌在文字中时按字符串拼接（变量须为标量）。可用变量：run.code、run.time、random.digits(n)、account.identity、
 * steps.&lt;前序步骤&gt;.input.*、steps.&lt;前序步骤&gt;.outputs.*；不支持表达式和函数。
 */
@Component
public class VerifyTemplateRenderer {

    static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^{}]*)}");
    static final Pattern RANDOM = Pattern.compile("random\\.digits\\((\\d{1,2})\\)");
    static final Pattern STEP_REF =
        Pattern.compile("steps\\.([a-z][A-Za-z0-9]{0,63})\\.(input|outputs)((?:\\.[A-Za-z_$][A-Za-z0-9_$-]*)*)");
    static final int MAX_RANDOM_DIGITS = 18;
    static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    /**
     * 渲染时可用的值。
     *
     * @param runCode 运行编号
     * @param runTime 运行开始时间（UTC ISO-8601）
     * @param accountIdentity 账号身份对象 {channelCode, channelAccountId, entityNo, accountVaultToken}
     * @param steps 前序步骤：步骤 ID → {input, outputs}
     */
    public record Scope(String runCode, String runTime, JsonNode accountIdentity, Map<String, StepValues> steps) {
    }

    /**
     * 前序步骤的实际输入与输出。
     *
     * @param input 实际输入
     * @param outputs 实际输出；未执行或执行前返回问题时为空对象
     */
    public record StepValues(JsonNode input, JsonNode outputs) {
    }

    /**
     * 渲染模板。
     *
     * @param template 输入模板
     * @param scope 可用值
     * @return 渲染后的新对象，不修改模板
     * @throws IllegalArgumentException 变量不合法或没有值；说明只含变量名
     */
    public JsonNode render(JsonNode template, Scope scope) {
        if (template.isObject()) {
            ObjectNode result = NODES.objectNode();
            template.properties().forEach(entry -> result.set(entry.getKey(), render(entry.getValue(), scope)));
            return result;
        }
        if (template.isArray()) {
            ArrayNode result = NODES.arrayNode();
            template.forEach(item -> result.add(render(item, scope)));
            return result;
        }
        if (!template.isString()) {
            return template.deepCopy();
        }
        String text = template.asString();
        Matcher whole = PLACEHOLDER.matcher(text);
        if (whole.matches()) {
            return resolve(whole.group(1).strip(), scope).deepCopy();
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        var builder = new StringBuilder();
        while (matcher.find()) {
            JsonNode value = resolve(matcher.group(1).strip(), scope);
            if (!value.isValueNode() || value.isNull()) {
                throw new IllegalArgumentException("嵌在文字中的模板变量必须是字符串、数字或布尔值：" + matcher.group(1).strip());
            }
            matcher.appendReplacement(builder, Matcher.quoteReplacement(value.asString()));
        }
        matcher.appendTail(builder);
        return NODES.stringNode(builder.toString());
    }

    /** 解析一个变量。 */
    JsonNode resolve(String variable, Scope scope) {
        switch (variable) {
            case "run.code" -> {
                return NODES.stringNode(scope.runCode());
            }
            case "run.time" -> {
                return NODES.stringNode(scope.runTime());
            }
            case "account.identity" -> {
                return scope.accountIdentity();
            }
            default -> {
            }
        }
        Matcher random = RANDOM.matcher(variable);
        if (random.matches()) {
            int digits = Integer.parseInt(random.group(1));
            if (digits < 1 || digits > MAX_RANDOM_DIGITS) {
                throw new IllegalArgumentException("random.digits 位数必须为 1～18：" + variable);
            }
            var builder = new StringBuilder();
            for (int index = 0; index < digits; index++) {
                builder.append(ThreadLocalRandom.current().nextInt(10));
            }
            return NODES.stringNode(builder.toString());
        }
        Matcher step = STEP_REF.matcher(variable);
        if (!step.matches()) {
            throw new IllegalArgumentException("不支持的模板变量：" + variable);
        }
        StepValues values = scope.steps().get(step.group(1));
        if (values == null) {
            throw new IllegalArgumentException("模板变量引用的步骤尚未执行：" + variable);
        }
        JsonNode current = "input".equals(step.group(2)) ? values.input() : values.outputs();
        for (String segment : segments(step.group(3))) {
            current = current == null ? null : current.get(segment);
        }
        if (current == null || current.isNull() || current.isMissingNode()) {
            throw new IllegalArgumentException("模板变量没有值：" + variable);
        }
        return current;
    }

    /**
     * 模板中出现的全部变量（去除首尾空白），按出现顺序。
     *
     * @param template 输入模板
     * @return 变量与所在字符串是否整串只有这一个变量
     */
    public static List<Variable> variables(JsonNode template) {
        var result = new ArrayList<Variable>();
        collect(template, result);
        return result;
    }

    /**
     * 变量的静态问题。
     *
     * @param variable 变量
     * @param previousSteps 前序步骤 ID
     * @return 问题说明；合法为 null
     */
    public static String problem(Variable variable, Set<String> previousSteps) {
        String name = variable.name();
        if ("run.code".equals(name) || "run.time".equals(name)) {
            return null;
        }
        if ("account.identity".equals(name)) {
            return variable.whole() ? null : "account.identity 是对象，只能整串使用";
        }
        Matcher random = RANDOM.matcher(name);
        if (random.matches()) {
            int digits = Integer.parseInt(random.group(1));
            return digits >= 1 && digits <= MAX_RANDOM_DIGITS ? null : "random.digits 位数必须为 1～18";
        }
        Matcher step = STEP_REF.matcher(name);
        if (!step.matches()) {
            return "不支持的模板变量：" + name;
        }
        return previousSteps.contains(step.group(1)) ? null : "只能引用前序步骤：" + step.group(1);
    }

    /** 是否引用前序步骤。 */
    public static boolean referencesSteps(Variable variable) {
        return STEP_REF.matcher(variable.name()).matches();
    }

    /**
     * 模板中的一个变量。
     *
     * @param name 去除首尾空白后的变量
     * @param whole 所在字符串是否整串只有这一个变量
     */
    public record Variable(String name, boolean whole) {
    }

    static void collect(JsonNode node, List<Variable> result) {
        if (node.isObject()) {
            node.properties().forEach(entry -> collect(entry.getValue(), result));
        } else if (node.isArray()) {
            node.forEach(item -> collect(item, result));
        } else if (node.isString()) {
            String text = node.asString();
            boolean whole = PLACEHOLDER.matcher(text).matches();
            Matcher matcher = PLACEHOLDER.matcher(text);
            while (matcher.find()) {
                result.add(new Variable(matcher.group(1).strip(), whole));
            }
        }
    }

    static List<String> segments(String path) {
        return path.isEmpty() ? List.of() : List.of(path.substring(1).split("\\."));
    }
}
