package com.paytech.risk.service.request;

/**
 * 已完成传输格式校验的 Operations 调用上下文。
 *
 * @param actorAccountId
 *            Operations 全局账号标识
 * @param sessionId
 *            Gateway 会话关联标识，原样保留
 */
public record RiskOperationRequestContext(long actorAccountId, String sessionId) {
}
