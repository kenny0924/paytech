package com.paytech.platform.support.accesscontrol.domain.authentication;

import java.time.Instant;

/** 准备与本地提交之间只传业务值，不将 Owner DataObject 暴露给 Web 模块。 */
public final class ClientAuthenticationPlans {
    /** 模型容器不实例化。 */
    private ClientAuthenticationPlans() {}
    public record Tokens(ClientAccountView context, String accessToken, Instant expiresAt,
        String refreshToken, Instant refreshExpiresAt) {
        /** 不打印会话秘密。 */
        @Override public String toString() { return "Tokens[REDACTED]"; }
    }
    public record Issue(long accountId, long sessionId, String contextType, Long tenantId, Long memberId,
        int accountEpoch, int credentialVersion,
        Instant issuedTime, Instant idleExpireTime, Instant absoluteExpireTime, int generation,
        long refreshId, String refreshLookup, String refreshHmac, int refreshKeyVersion,
        long signingKeyId, int signingKeyVersion, Tokens tokens) {
        /** 不打印待提交的签发材料。 */
        @Override public String toString() { return "Issue[REDACTED]"; }
    }
    public record LoginPlan(long accountId, int expectedVersion, int expectedCredentialVersion,
        int expectedEpoch, boolean passwordMismatch, Issue issue) {
        /** 不打印临时凭据。 */
        @Override public String toString() { return "LoginPlan[REDACTED]"; }
    }
    public record RefreshIdentity(long accountId, long sessionId, String lookup) {}
    public record RefreshPlan(long accountId, long sessionId, String lookup, String computedHmac,
        int keyVersion, int expectedSessionVersion, Issue issue, Issue fallback) {
        /** 不打印 HMAC 或令牌。 */
        @Override public String toString() { return "RefreshPlan[REDACTED]"; }
    }
    /**
     * 切换上下文的准备结果
     * <li>memberId 为空表示返回账号上下文；否则为进入的目标成员及其租户。</li>
     */
    public record SwitchPlan(LogoutProof proof, Long memberId, Long tenantId, Issue issue) {
        /** 不打印秘密校验材料或待签发令牌。 */
        @Override public String toString() { return "SwitchPlan[REDACTED]"; }
    }
    public record LogoutProof(long accountId, long sessionId, String lookup, String computedHmac, int keyVersion) {
        /** 不打印秘密校验材料。 */
        @Override public String toString() { return "LogoutProof[REDACTED]"; }
    }
}
