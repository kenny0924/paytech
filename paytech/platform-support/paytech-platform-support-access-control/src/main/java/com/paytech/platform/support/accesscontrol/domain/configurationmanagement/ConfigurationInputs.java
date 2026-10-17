package com.paytech.platform.support.accesscontrol.domain.configurationmanagement;
import com.paytech.foundation.common.error.InvalidParameterException;
/** 管理配置的最小输入约束，不承担 CRUD 或业务状态规则。 */
public final class ConfigurationInputs {
    private ConfigurationInputs() {}
    /** 稳定编码保留大小写，拒绝未知形状。 */
    public static String code(String value, int max) {
        if (value == null || value.length() > max || !value.matches("[A-Z][A-Z0-9_]*")) {
            throw new InvalidParameterException("编码必须由大写字母、数字或下划线组成且不超过长度限制");
        }
        return value;
    }
    /** 人类文本去除首尾空白，不允许空说明。 */
    public static String text(String value, int max, String field) {
        String result = value == null ? "" : value.strip();
        if (result.isEmpty() || result.length() > max) throw new InvalidParameterException(field + " 不能为空或超长");
        return result;
    }
    /** HTTP 主键不允许整数截断或浮点转换。 */
    public static Long id(String value) {
        if (value == null) return null;
        try {
            long result = Long.parseLong(value);
            if (result <= 0 || !value.matches("[1-9][0-9]*")) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException e) { throw new InvalidParameterException("ID 必须为有效十进制主键"); }
    }
    /** 闭合枚举未知值返回 400 而不是未知服务器异常。 */
    public static <E extends Enum<E>> E enumeration(Class<E> type, String value) {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException | NullPointerException e) { throw new InvalidParameterException(type.getSimpleName() + " 不是支持的值"); }
    }
}
