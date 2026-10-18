package com.paytech.foundation.web.request;

/**
 * 已完成传输格式校验的 Operations 调用上下文（由 Gateway 已验证身份转发的 Actor 与 Session）。
 *
 * @author Claude
 */
public final class OperationRequestContext {

    /** Operations 全局账号标识。 */
    private final long actorAccountId;

    /** Gateway 会话关联标识，原样保留。 */
    private final String sessionId;

    /**
     * 创建上下文。
     *
     * @param actorAccountId Operations 全局账号标识
     * @param sessionId Gateway 会话关联标识
     */
    public OperationRequestContext(long actorAccountId, String sessionId) {
        this.actorAccountId = actorAccountId;
        this.sessionId = sessionId;
    }

    /**
     * Operations 全局账号标识。
     *
     * @return 正整数
     */
    public long getActorAccountId() {
        return actorAccountId;
    }

    /**
     * Gateway 会话关联标识。
     *
     * @return 可打印 ASCII，最长 128
     */
    public String getSessionId() {
        return sessionId;
    }
}
