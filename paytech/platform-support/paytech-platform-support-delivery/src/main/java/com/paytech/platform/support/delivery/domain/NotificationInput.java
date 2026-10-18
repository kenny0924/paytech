package com.paytech.platform.support.delivery.domain;

import java.nio.charset.StandardCharsets;
import java.util.IllformedLocaleException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 普通通知输入的规范化与无表达式纯文本渲染。 */
public final class NotificationInput {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern SLOT = Pattern.compile("\\$\\{([A-Za-z][A-Za-z0-9_]{0,63})}");
    private static final Pattern RESERVED = Pattern.compile(
        ".*(?:password|secret|token|otp|verificationcode|resetlink|verificationlink).*", Pattern.CASE_INSENSITIVE);

    /** 即时发送模板正文必须包含的链接参数名。 */
    public static final String TRANSIENT_LINK_PARAMETER = "link";

    /** 运营维护的标题模板上限（UTF-8 字节）；渲染后仍按发送规则限制 255 字符。 */
    static final int SUBJECT_TEMPLATE_MAX_BYTES = 1024;

    /** 运营维护的正文模板上限（UTF-8 字节），使保存请求不超过 Gateway 64 KiB 请求体上限。 */
    static final int BODY_TEMPLATE_MAX_BYTES = 60000;

    /** 工具无实例。 */
    private NotificationInput() {}

    /** 首期与平台登录邮箱一致，去除首尾空白、转小写，不折叠加号或点号别名。 */
    public static String canonicalEmail(String email) {
        String canonical = email.strip().toLowerCase(Locale.ROOT);
        if (!canonical.matches("[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")
            || canonical.length() > 254 || canonical.indexOf('@') > 64 || canonical.startsWith(".")
            || canonical.contains("..") || canonical.contains(".@")) {
            throw new InvalidParameterException("邮箱格式不受支持");
        }
        return canonical;
    }

    /** 精确语言采用规范 BCP 47 形式，不接受 und、非法标签或隐式回退。 */
    public static String locale(String value) {
        try {
            String language = new Locale.Builder().setLanguageTag(value).build().toLanguageTag();
            if (language.equals("und") || language.length() > 35)
                throw new IllegalArgumentException();
            return language;
        } catch (IllegalArgumentException | IllformedLocaleException failure) {
            throw new InvalidParameterException("通知语言不合法");
        }
    }

    /** 仅持久化有界普通字符串参数；字段名检查不替代业务 Owner 的敏感数据责任。 */
    public static String parameters(Map<String, String> values) {
        if (values == null || values.isEmpty())
            return null;
        if (values.size() > 64 || values.entrySet().stream()
            .anyMatch(e -> e.getKey() == null || !e.getKey().matches("[A-Za-z][A-Za-z0-9_]{0,63}")
                || RESERVED.matcher(e.getKey()).matches() || e.getValue() == null || e.getValue().length() > 2048)) {
            throw new InvalidParameterException("通知只接受普通字符串参数，禁止认证秘密参数");
        }
        String encoded = JSON.writeValueAsString(values);
        if (encoded.getBytes(StandardCharsets.UTF_8).length > 16384) {
            throw new InvalidParameterException("通知参数超过 16 KiB");
        }
        return encoded;
    }

    /**
     * 即时发送的临时参数：只在内存中渲染一次，不持久化、不序列化
     * <li>1 至 8 个键，键与普通参数格式相同，值非空、不超过 2048 字符且不含换行以外的控制字符</li>
     * <li>不做普通参数的认证秘密键名检查：该检查防的是落库，这里本来就不落库</li>
     *
     * @return 不可变副本
     */
    public static Map<String, String> transientParameters(Map<String, String> values) {
        if (values == null || values.isEmpty() || values.size() > 8 || values.entrySet().stream()
            .anyMatch(e -> e.getKey() == null || !e.getKey().matches("[A-Za-z][A-Za-z0-9_]{0,63}")
                || e.getValue() == null || e.getValue().isEmpty() || e.getValue().length() > 2048
                || e.getValue().codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n'))) {
            throw new InvalidParameterException("即时发送参数不合法");
        }
        return Map.copyOf(values);
    }

    /** 已持久化参数 JSON 的名称，升序；空值返回空列表，不暴露参数值。 */
    public static List<String> parameterKeys(String parameters) {
        if (parameters == null) {
            return List.of();
        }
        return JSON.readValue(parameters, new TypeReference<Map<String, String>>() {}).keySet().stream().sorted()
            .toList();
    }

    /** 同名覆盖为一层 Map 覆盖；输入没有对象、数组或 null 值。 */
    public static Map<String, String> merge(String defaults, String overrides) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (defaults != null)
            merged.putAll(JSON.readValue(defaults, new TypeReference<Map<String, String>>() {}));
        if (overrides != null)
            merged.putAll(JSON.readValue(overrides, new TypeReference<Map<String, String>>() {}));
        parameters(merged);
        return merged;
    }

    /** 替换已声明占位符，缺失参数拒绝，不执行脚本、表达式或二次插值。 */
    public static NotificationMessage render(String subject, String body, Map<String, String> parameters) {
        if (subject == null || subject.isBlank() || body == null || body.isBlank()) {
            throw new BizCheckFailException("邮件模板标题或正文为空");
        }
        String title = substitute(subject, parameters);
        String content = substitute(body, parameters);
        if (title.length() > 255 || title.indexOf('\r') >= 0 || title.indexOf('\n') >= 0
            || content.getBytes(StandardCharsets.UTF_8).length > 65535) {
            throw new BizCheckFailException("渲染后的邮件超过限制或标题包含换行");
        }
        return new NotificationMessage(title, content);
    }

    /** 将模板字面量与值分开拼接，参数中的占位符不重新解释。 */
    static String substitute(String template, Map<String, String> parameters) {
        if (template.getBytes(StandardCharsets.UTF_8).length > 65535)
            throw new BizCheckFailException("邮件模板超过限制");
        var matcher = SLOT.matcher(template);
        StringBuilder result = new StringBuilder();
        int position = 0;
        while (matcher.find()) {
            String literal = template.substring(position, matcher.start());
            if (literal.contains("${"))
                throw new BizCheckFailException("模板占位符格式不合法");
            result.append(literal);
            String value = parameters.get(matcher.group(1));
            if (value == null)
                throw new BizCheckFailException("邮件模板缺少必要参数");
            result.append(value);
            position = matcher.end();
        }
        String tail = template.substring(position);
        if (tail.contains("${"))
            throw new BizCheckFailException("模板占位符格式不合法");
        return result.append(tail).toString();
    }

    /**
     * 按出现顺序列出模板中的占位符名称（去重）
     * <li>占位符外出现残缺的 ${ 时拒绝，与发送时的渲染规则一致</li>
     */
    public static List<String> placeholders(String template) {
        var matcher = SLOT.matcher(template);
        var names = new LinkedHashSet<String>();
        int position = 0;
        while (matcher.find()) {
            if (template.substring(position, matcher.start()).contains("${")) {
                throw new BizCheckFailException("模板占位符格式不合法");
            }
            names.add(matcher.group(1));
            position = matcher.end();
        }
        if (template.substring(position).contains("${")) {
            throw new BizCheckFailException("模板占位符格式不合法");
        }
        return List.copyOf(names);
    }

    /**
     * 运营保存语言内容前的检查，规则与发送时相同
     *
     * @param requiredParameters 正文必须包含的占位符，普通模板为空
     * @return 字段问题；为空表示可保存
     */
    public static List<NotificationTemplateIssue> contentIssues(String subject, String body,
        List<String> requiredParameters) {
        var issues = new ArrayList<NotificationTemplateIssue>();
        String subjectIssue = subjectIssue(subject);
        if (subjectIssue != null) {
            issues.add(new NotificationTemplateIssue("subjectTemplate", subjectIssue));
        }
        String bodyIssue = bodyIssue(body, requiredParameters);
        if (bodyIssue != null) {
            issues.add(new NotificationTemplateIssue("bodyTemplate", bodyIssue));
        }
        return issues;
    }

    /** 标题非空、不换行、不超过字节上限且占位符合法；无问题返回空。 */
    static String subjectIssue(String subject) {
        if (subject == null || subject.isBlank()) {
            return "标题模板不能为空";
        }
        if (subject.indexOf('\r') >= 0 || subject.indexOf('\n') >= 0) {
            return "标题模板不能换行";
        }
        if (subject.getBytes(StandardCharsets.UTF_8).length > SUBJECT_TEMPLATE_MAX_BYTES) {
            return "标题模板不能超过 " + SUBJECT_TEMPLATE_MAX_BYTES + " 字节";
        }
        return placeholderIssue(subject);
    }

    /** 正文非空、不超过字节上限、占位符合法且包含必需参数；无问题返回空。 */
    static String bodyIssue(String body, List<String> requiredParameters) {
        if (body == null || body.isBlank()) {
            return "正文模板不能为空";
        }
        if (body.getBytes(StandardCharsets.UTF_8).length > BODY_TEMPLATE_MAX_BYTES) {
            return "正文模板不能超过 " + BODY_TEMPLATE_MAX_BYTES + " 字节";
        }
        String issue = placeholderIssue(body);
        if (issue != null) {
            return issue;
        }
        var present = placeholders(body);
        var missing = requiredParameters.stream().filter(name -> !present.contains(name)).toList();
        if (!missing.isEmpty()) {
            return "认证邮件模板正文必须包含 ${" + String.join("}、${", missing) + "}";
        }
        return null;
    }

    /** 占位符格式问题的说明；合法返回空。 */
    static String placeholderIssue(String template) {
        try {
            placeholders(template);
            return null;
        } catch (BizCheckFailException invalid) {
            return "占位符格式不合法，只能使用 ${名称}，名称以字母开头、由字母数字下划线组成";
        }
    }

    /**
     * 预览渲染：只替换已提供的参数，缺少的占位符原样保留并列出
     * <li>不校验长度与换行，保存时另行检查；不调用 Vault 或供应商</li>
     */
    public static NotificationPreview preview(String subject, String body, Map<String, String> parameters) {
        var missing = new LinkedHashSet<String>();
        String title = lenientSubstitute(subject, parameters, missing);
        String content = lenientSubstitute(body, parameters, missing);
        return new NotificationPreview(title, content, List.copyOf(missing));
    }

    /** 替换已提供的参数，缺失名称写入 missing；参数值中的占位符不重新解释。 */
    static String lenientSubstitute(String template, Map<String, String> parameters, LinkedHashSet<String> missing) {
        var matcher = SLOT.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = parameters.get(matcher.group(1));
            if (value == null) {
                missing.add(matcher.group(1));
            }
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(
                value == null ? matcher.group() : value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
