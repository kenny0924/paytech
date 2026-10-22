package com.paytech.channel.connector.protocol.domainservice.validator;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.enums.ExecuteStatus;
import com.paytech.channel.connector.protocol.domain.ValidationIssue;
import com.paytech.channel.connector.protocol.domain.execution.FieldSchema;
import com.paytech.channel.connector.protocol.domain.execution.FieldSchemaViolation;
import com.paytech.channel.connector.protocol.domain.verify.VerifyMatch;
import com.paytech.channel.connector.protocol.domain.verify.VerifyStep;
import com.paytech.channel.connector.protocol.domainservice.verify.VerifyTemplateRenderer;

import lombok.AllArgsConstructor;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

/**
 * 验证用例步骤的静态校验（沙箱验证技术方案第 4 节）：结构、步骤 ID、标准 API 已由本渠道实现、模板变量、
 * 期望与输出路径。输入不引用前序步骤、且期望不是执行前问题时，以示例值渲染后按标准 API 请求字段校验；
 * 引用前序步骤时运行时再校验；期望问题（如 422）的负向用例本就有意提交不合法输入，不做结构校验。
 * 纯内存校验，不执行流程或外部请求。
 */
@Component
@AllArgsConstructor
public class VerifyCaseValidator {

    /** 每个用例最多步骤数。 */
    public static final int MAX_STEPS = 10;

    /** 正则期望最长字符数。 */
    public static final int MAX_REGEX_LENGTH = 256;

    /** 每步最多输出期望数。 */
    public static final int MAX_OUTPUT_EXPECTATIONS = 32;

    static final Pattern STEP_ID = Pattern.compile("[a-z][A-Za-z0-9]{0,63}");
    static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    static final Set<String> STEP_KEYS = Set.of("id", "standardApiCode", "input", "expect");
    static final Set<String> EXPECT_KEYS = Set.of("executeStatus", "problem", "returnId", "outputs");
    static final Set<String> OUTPUT_KEYS = Set.of("path", "match", "value");

    private final VerifyTemplateRenderer renderer;

    /**
     * 本渠道对某个标准 API 的实现所提供的合同。
     */
    @Getter
    @AllArgsConstructor
    public static class StepContract {

        /** 标准 API 输入结构。 */
        private final FieldSchema inputSchema;

        /** 标准 API 输出结构（outputs 之下）。 */
        private final FieldSchema outputSchema;

        /** 实现流程的出口 ID。 */
        private final Set<String> returnIds;
    }

    /**
     * 校验步骤。
     *
     * @param steps steps_json
     * @param contracts 标准 API 编码 → 本渠道实现的合同；没有实现时不在映射中
     * @param sample 示例渲染值（运行编号、时间与账号身份）
     * @return 发现的问题；为空表示通过
     */
    public List<ValidationIssue> validate(JsonNode steps, Map<String, StepContract> contracts,
        VerifyTemplateRenderer.Scope sample) {
        var issues = new ArrayList<ValidationIssue>();
        if (steps == null || !steps.isArray() || steps.isEmpty() || steps.size() > MAX_STEPS) {
            issues.add(new ValidationIssue("STEPS_INVALID", "steps", "步骤必须是 1～" + MAX_STEPS + " 项的数组"));
            return issues;
        }
        var ids = new LinkedHashSet<String>();
        for (int index = 0; index < steps.size(); index++) {
            step(steps.get(index), "steps[" + index + "]", ids, contracts, sample, issues);
        }
        return issues;
    }

    void step(JsonNode step, String location, Set<String> previous, Map<String, StepContract> contracts,
        VerifyTemplateRenderer.Scope sample, List<ValidationIssue> issues) {
        if (!step.isObject()) {
            issues.add(new ValidationIssue("STEP_INVALID", location, "步骤必须是对象"));
            return;
        }
        keys(step, location, STEP_KEYS, Set.of("id", "standardApiCode", "input", "expect"), issues);
        String id = step.path("id").asString("");
        if (!STEP_ID.matcher(id).matches()) {
            issues.add(new ValidationIssue("STEP_ID_INVALID", location + ".id", "步骤 ID 须以小写字母开头，只含字母与数字，最多 64 字符"));
        } else if (previous.contains(id)) {
            issues.add(new ValidationIssue("STEP_ID_DUPLICATED", location + ".id", "步骤 ID 重复：" + id));
        }
        String code = step.path("standardApiCode").asString("");
        StepContract contract = contracts.get(code);
        if (!CODE.matcher(code).matches() || contract == null) {
            issues.add(new ValidationIssue("STANDARD_API_NOT_IMPLEMENTED", location + ".standardApiCode",
                "本渠道没有实现该标准 API，或实现配置未通过运行前校验：" + code));
        }
        input(step.path("input"), location + ".input", previous, step.path("expect").has("problem") ? null : contract,
            sample, issues);
        expect(step.path("expect"), location + ".expect", contract, issues);
        previous.add(id);
    }

    void input(JsonNode input, String location, Set<String> previous, StepContract contract,
        VerifyTemplateRenderer.Scope sample, List<ValidationIssue> issues) {
        if (!input.isObject()) {
            issues.add(new ValidationIssue("INPUT_INVALID", location, "输入必须是对象"));
            return;
        }
        var variables = VerifyTemplateRenderer.variables(input);
        boolean valid = true;
        for (var variable : variables) {
            String problem = VerifyTemplateRenderer.problem(variable, previous);
            if (problem != null) {
                valid = false;
                issues.add(new ValidationIssue("TEMPLATE_VARIABLE_INVALID", location, problem));
            }
        }
        if (!valid || contract == null || variables.stream().anyMatch(VerifyTemplateRenderer::referencesSteps)) {
            return;
        }
        try {
            contract.getInputSchema().fromJson(renderer.render(input, sample), "");
        } catch (FieldSchemaViolation violation) {
            String path = violation.getPath().isEmpty() ? location : location + "." + violation.getPath();
            issues.add(new ValidationIssue("INPUT_SCHEMA_MISMATCH", path, violation.getMessage()));
        }
    }

    void expect(JsonNode expect, String location, StepContract contract, List<ValidationIssue> issues) {
        if (!expect.isObject()) {
            issues.add(new ValidationIssue("EXPECT_INVALID", location, "期望必须是对象"));
            return;
        }
        keys(expect, location, EXPECT_KEYS, Set.of(), issues);
        boolean executed = expect.has("executeStatus");
        if (executed == expect.has("problem")) {
            issues.add(new ValidationIssue("EXPECT_INVALID", location, "executeStatus 与 problem 必须且只能填写一个"));
            return;
        }
        if (!executed) {
            problem(expect.path("problem"), location + ".problem", issues);
            if (expect.has("returnId") || expect.has("outputs")) {
                issues.add(new ValidationIssue("EXPECT_INVALID", location, "期望问题时不能再写 returnId 或 outputs"));
            }
            return;
        }
        if (ExecuteStatus.fromCode(expect.path("executeStatus").asString("")) == null) {
            issues.add(new ValidationIssue("EXPECT_INVALID", location + ".executeStatus", "只允许 SUCCESS、FAIL、ERROR"));
        }
        if (expect.has("returnId") && (!expect.get("returnId").isString()
            || contract != null && !contract.getReturnIds().contains(expect.get("returnId").asString()))) {
            issues.add(new ValidationIssue("RETURN_UNKNOWN", location + ".returnId", "实现流程中没有该出口"));
        }
        if (expect.has("outputs")) {
            outputs(expect.get("outputs"), location + ".outputs", contract, issues);
        }
    }

    void problem(JsonNode problem, String location, List<ValidationIssue> issues) {
        if (!problem.isObject()) {
            issues.add(new ValidationIssue("EXPECT_INVALID", location, "problem 必须是对象"));
            return;
        }
        keys(problem, location, Set.of("status", "code"), Set.of("status"), issues);
        JsonNode status = problem.path("status");
        if (!status.isIntegralNumber() || status.asInt() < 400 || status.asInt() > 599) {
            issues.add(new ValidationIssue("EXPECT_INVALID", location + ".status", "status 必须是 400～599 的整数"));
        }
        if (problem.has("code") && !CODE.matcher(problem.get("code").asString("")).matches()) {
            issues.add(new ValidationIssue("EXPECT_INVALID", location + ".code", "code 必须是大写编码"));
        }
    }

    void outputs(JsonNode outputs, String location, StepContract contract, List<ValidationIssue> issues) {
        if (!outputs.isArray() || outputs.size() > MAX_OUTPUT_EXPECTATIONS) {
            issues.add(new ValidationIssue("EXPECT_INVALID", location, "outputs 必须是最多 " + MAX_OUTPUT_EXPECTATIONS + " 项的数组"));
            return;
        }
        for (int index = 0; index < outputs.size(); index++) {
            JsonNode output = outputs.get(index);
            String at = location + "[" + index + "]";
            if (!output.isObject()) {
                issues.add(new ValidationIssue("EXPECT_INVALID", at, "输出期望必须是对象"));
                continue;
            }
            keys(output, at, OUTPUT_KEYS, Set.of("path", "match"), issues);
            String path = output.path("path").asString("");
            if (path.isEmpty() || path.contains("[") || contract != null && contract.getOutputSchema().at(path) == null) {
                issues.add(new ValidationIssue("OUTPUT_PATH_UNKNOWN", at + ".path", "不是标准 API 的响应字段：" + path));
            }
            VerifyMatch match = match(output.path("match").asString(""));
            if (match == null) {
                issues.add(new ValidationIssue("MATCH_INVALID", at + ".match", "只允许 EQUALS、EXISTS、ABSENT、REGEX、ONE_OF"));
                continue;
            }
            String problem = valueProblem(match, output.get("value"));
            if (problem != null) {
                issues.add(new ValidationIssue("VALUE_INVALID", at + ".value", problem));
            }
        }
    }

    /** 匹配方式对期望值的要求；合法为 null。 */
    static String valueProblem(VerifyMatch match, JsonNode value) {
        return switch (match) {
            case EXISTS, ABSENT -> value == null ? null : "EXISTS、ABSENT 不填写期望值";
            case EQUALS -> value == null ? "EQUALS 需要期望值" : null;
            case ONE_OF -> value == null || !value.isArray() || value.isEmpty() ? "ONE_OF 需要非空数组" : null;
            case REGEX -> regexProblem(value);
        };
    }

    static String regexProblem(JsonNode value) {
        if (value == null || !value.isString() || value.asString().isEmpty()
            || value.asString().length() > MAX_REGEX_LENGTH) {
            return "REGEX 需要 1～" + MAX_REGEX_LENGTH + " 字符的正则";
        }
        try {
            Pattern.compile(value.asString());
            return null;
        } catch (PatternSyntaxException invalid) {
            return "正则无法编译";
        }
    }

    static VerifyMatch match(String value) {
        return Arrays.stream(VerifyMatch.values()).filter(item -> item.name().equals(value)).findFirst().orElse(null);
    }

    /** 解析已通过校验的步骤。 */
    public static List<VerifyStep> parse(JsonNode steps) {
        var result = new ArrayList<VerifyStep>();
        for (JsonNode step : steps) {
            JsonNode expect = step.path("expect");
            JsonNode problem = expect.get("problem");
            var outputs = new ArrayList<VerifyStep.OutputExpectation>();
            expect.path("outputs").forEach(output -> outputs.add(new VerifyStep.OutputExpectation(
                output.path("path").asString(), match(output.path("match").asString()), output.get("value"))));
            result.add(new VerifyStep(step.path("id").asString(), step.path("standardApiCode").asString(),
                step.get("input"), problem == null ? expect.path("executeStatus").asString() : null,
                problem == null ? null : problem.path("status").asInt(),
                problem == null || !problem.has("code") ? null : problem.get("code").asString(),
                expect.has("returnId") ? expect.get("returnId").asString() : null, List.copyOf(outputs)));
        }
        return result;
    }

    static void keys(JsonNode node, String location, Set<String> allowed, Set<String> required,
        List<ValidationIssue> issues) {
        node.propertyNames().stream().filter(name -> !allowed.contains(name)).forEach(name -> issues
            .add(new ValidationIssue("PROPERTY_UNKNOWN", location + "." + name, "不支持的属性")));
        required.stream().filter(name -> !node.has(name)).sorted().forEach(name -> issues
            .add(new ValidationIssue("PROPERTY_MISSING", location + "." + name, "缺少必填属性")));
    }
}
