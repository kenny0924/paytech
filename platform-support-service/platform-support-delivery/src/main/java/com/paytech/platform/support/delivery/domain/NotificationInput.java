package com.paytech.platform.support.delivery.domain;

import java.nio.charset.StandardCharsets;
import java.util.IllformedLocaleException;
import java.util.LinkedHashMap;
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
}
