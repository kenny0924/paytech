package com.paytech.platform.support.workflow.core.common;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * 工作流配置与变量值共用的格式规则：key、编码、十进制、日期与布尔文本。
 */
public final class WorkflowValueFormats {

    /** 节点、连线、动作与字段 key：字母开头，字母、数字或下划线，最长 64。 */
    private static final Pattern KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");

    /** 回调编码：大写字母开头，大写字母、数字或下划线，最长 64。 */
    private static final Pattern CALLBACK_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    /** 普通十进制：不接受指数、前导零与正号。 */
    private static final Pattern DECIMAL = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?");

    /** 日期：YYYY-MM-DD。 */
    private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

    /** MIME 类型：type/subtype，小写。 */
    private static final Pattern MIME_TYPE = Pattern.compile("[a-z0-9][a-z0-9!#$&^_.+-]{0,63}/[a-z0-9][a-z0-9!#$&^_.+-]{0,126}");

    private WorkflowValueFormats() {
    }

    /**
     * 是否为合法 key。
     *
     * @param value 候选值
     * @return 合法返回 true
     */
    public static boolean isKey(String value) {
        return value != null && KEY.matcher(value).matches();
    }

    /**
     * 是否为合法回调编码。
     *
     * @param value 候选值
     * @return 合法返回 true
     */
    public static boolean isCallbackCode(String value) {
        return value != null && CALLBACK_CODE.matcher(value).matches();
    }

    /**
     * 是否为普通十进制文本。
     *
     * @param value 候选值
     * @return 合法返回 true
     */
    public static boolean isDecimal(String value) {
        return value != null && DECIMAL.matcher(value).matches();
    }

    /**
     * 是否为存在的 YYYY-MM-DD 日期。
     *
     * @param value 候选值
     * @return 合法返回 true
     */
    public static boolean isDate(String value) {
        if (value == null || !DATE.matcher(value).matches()) {
            return false;
        }
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    /**
     * 是否为 true / false。
     *
     * @param value 候选值
     * @return 合法返回 true
     */
    public static boolean isBoolean(String value) {
        return "true".equals(value) || "false".equals(value);
    }

    /**
     * 是否为合法 MIME 类型。
     *
     * @param value 候选值
     * @return 合法返回 true
     */
    public static boolean isMimeType(String value) {
        return value != null && MIME_TYPE.matcher(value).matches();
    }

    /**
     * 是否为空白文本。
     *
     * @param value 候选值
     * @return null 或只含空白返回 true
     */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * JSON_SEARCH / LIKE 的字面量：以 ! 为转义符转义 !、% 与 _。
     *
     * @param value 原文
     * @return 转义后的文本
     */
    public static String likeLiteral(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
