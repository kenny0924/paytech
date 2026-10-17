package com.paytech.risk.decision.domain.variable;

import static com.paytech.risk.decision.domainservice.service.RiskVariableException.Reason.INVALID_VARIABLE_DEFINITION;
import com.paytech.risk.decision.domainservice.service.RiskVariableException;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.VariableFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** 规则、流程与集合共用的标量规范化；仅依赖类型及格式，不认识任何业务变量编码。 */
public final class VariableValueNormalizer {
    /** 纯值能力不持有外部状态。 */
    private VariableValueNormalizer() { }

    /** 将允许的 JSON 标量转为规范字符串或 Boolean；拒绝隐式类型转换。 */
    public static Object normalize(DecisionValueType type, VariableFormat format, Object value) {
        if (type == DecisionValueType.BOOLEAN) {
            if (!(value instanceof Boolean)) { throw invalid("布尔值必须为 true 或 false"); }
            return value;
        }
        if (!(value instanceof String text)) { throw invalid("该类型必须使用字符串值"); }
        return switch (type) {
            case STRING -> string(format, text);
            case NUMBER -> decimal(text);
            case DATETIME -> dateTime(text);
            case DURATION -> duration(text);
            case BOOLEAN -> throw invalid("布尔值类型错误");
        };
    }

    /** 生成去重键；数字去尾零，字符串保持原始大小写和空格。 */
    public static String key(DecisionValueType type, Object normalized) {
        if (type == DecisionValueType.NUMBER) {
            return new BigDecimal((String) normalized).stripTrailingZeros().toPlainString();
        }
        return normalized.toString();
    }

    /** 按通用字符串格式验证，只有 BCP47 执行既有语言标签规范化。 */
    static String string(VariableFormat format, String value) {
        int length = value.codePointCount(0, value.length());
        if (length < 1 || length > 512 || value.codePoints().anyMatch(c -> Character.isISOControl(c) || c <= 0xffff && Character.isSurrogate((char) c))) {
            throw invalid("字符串须为 1–512 个字符且不包含控制字符");
        }
        boolean valid = switch (format) {
            case TEXT -> true;
            case TOKEN -> value.matches("[A-Z][A-Z0-9_]{0,63}");
            case UPPER_ALPHA_2 -> value.matches("[A-Z]{2}");
            case UPPER_ALPHA_3 -> value.matches("[A-Z]{3}");
            case DIGITS_4 -> value.matches("[0-9]{4}");
            case POSITIVE_ID -> validPositiveId(value);
            case BCP47 -> validLocale(value);
            case NONE -> false;
        };
        if (!valid) { throw invalid("字符串不符合格式 " + format); }
        return format == VariableFormat.BCP47 ? Locale.forLanguageTag(value).toLanguageTag() : value;
    }

    /** 金额和普通数值均保留八位精度，不舍入、不接受指数。 */
    static String decimal(String value) {
        if (!value.matches("-?(?:0|[1-9][0-9]{0,15})(?:\\.[0-9]{1,8})?")) {
            throw invalid("数值最多 16 位整数、8 位小数，不能使用指数");
        }
        return new BigDecimal(value).setScale(8).toPlainString();
    }

    /** 接受且输出 UTC 微秒时间，拒绝无效日历日期。 */
    static String dateTime(String value) {
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{6}Z")) {
            throw invalid("时间须为 UTC 六位微秒格式");
        }
        try {
            Instant time = Instant.parse(value);
            String normalized = new DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(time);
            if (!normalized.equals(value) || value.compareTo("1000-") < 0) { throw invalid("时间超出数据库范围或日期无效"); }
            return normalized;
        } catch (DateTimeParseException failure) { throw invalid("时间日期无效"); }
    }

    /** 持续时长使用规范非负有符号 BIGINT 毫秒字符串。 */
    static String duration(String value) {
        if (!value.matches("0|[1-9][0-9]{0,18}")) { throw invalid("时长必须为非负毫秒整数"); }
        try { return Long.toString(Long.parseLong(value)); }
        catch (NumberFormatException failure) { throw invalid("时长超出 BIGINT 范围"); }
    }

    /** 保留正整数 ID 的字符串身份，拒绝前导零和溢出。 */
    static boolean validPositiveId(String value) {
        if (!value.matches("[1-9][0-9]{0,18}")) { return false; }
        try { return Long.parseLong(value) > 0; }
        catch (NumberFormatException failure) { return false; }
    }

    /** 沿用当前受支持语言标签范围，不猜测无效标签。 */
    static boolean validLocale(String value) {
        if (!value.matches("[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*")) { return false; }
        Locale locale = Locale.forLanguageTag(value);
        return !locale.getLanguage().isBlank() && !locale.toLanguageTag().contains("und");
    }

    /** 构造不包含原始值的校验失败。 */
    static RiskVariableException invalid(String message) {
        return new RiskVariableException(INVALID_VARIABLE_DEFINITION, message);
    }
}
