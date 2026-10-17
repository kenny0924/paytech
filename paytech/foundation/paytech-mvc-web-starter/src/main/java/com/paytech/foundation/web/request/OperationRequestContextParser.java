package com.paytech.foundation.web.request;

/**
 * Operations 入口的严格元数据解析边界；格式不合法统一抛 {@code InvalidParameterException}（400 {@code INVALID_REQUEST}）。
 *
 * @author Claude
 */
public interface OperationRequestContextParser {

    /**
     * 解析 Gateway 已验证身份对应的 Actor 与 Session 传输值。
     *
     * @param actorAccountId Actor 的 canonical 正整数文本
     * @param sessionId 可打印 ASCII 会话标识
     * @return 原样保留 Session 的调用上下文
     */
    OperationRequestContext parse(String actorAccountId, String sessionId);

    /**
     * 解析 canonical 正整数标识。
     *
     * @param parameterName 安全的参数名称，仅用于诊断
     * @param rawValue 原始传输文本
     * @return 正的 Java long
     */
    long parsePositiveId(String parameterName, String rawValue);

    /**
     * 校验必填的大写目录 Code。
     *
     * @param parameterName 安全的参数名称，仅用于诊断
     * @param rawValue 原始传输文本
     * @return 未经改写的合法 Code
     */
    String parseRequiredUpperCode(String parameterName, String rawValue);

    /**
     * 校验可选的大写目录 Code。
     *
     * @param parameterName 安全的参数名称，仅用于诊断
     * @param rawValue 原始传输文本或 null
     * @return null 或未经改写的合法 Code
     */
    String parseOptionalUpperCode(String parameterName, String rawValue);
}
