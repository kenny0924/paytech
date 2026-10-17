package com.paytech.platform.support.auditsecurity.domain.audit;

/** 迟到事件遇到封闭发生日链的内部丢弃信号，不向 HTTP 发布业务错误。 */
public final class AuditChainClosedException extends IllegalStateException {
    public AuditChainClosedException() {
        super("Operations 发生日审计链不可追加");
    }
}
