package com.paytech.platform.support.accesscontrol.domain.authentication;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.domain.authentication.ClientEmailChallengePolicy.LinkMaterial;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengePurpose;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengeStatus;

/** 跨事务准备结果只携带明确业务事实，不暴露 Owner DataObject。 */
public final class ClientRegistrationModels {
    /** 业务值容器不实例化。 */
    private ClientRegistrationModels() {}

    public record EmailIdentity(String canonicalEmail, String emailToken, String emailMasked, String passwordVerifier,
        String resourceKey) {
        /** 隐藏邮箱和凭据。 */
        @Override
        public String toString() {
            return "EmailIdentity[REDACTED]";
        }
    }

    public record ChallengeDraft(long id, long accountId, ClientChallengePurpose purpose, String scopeHash,
        String secretHmac, int keyVersion, Instant expiresAt, Instant issuedAt, Long previousId,
        Integer previousVersion, ClientChallengeStatus previousStatus, LinkMaterial link) {
        /** 隐藏链接秘密和校验摘要。 */
        @Override
        public String toString() {
            return "ChallengeDraft[REDACTED]";
        }
    }

    public record IssuePlan(EmailIdentity identity, ChallengeDraft challenge, long passwordId, boolean newAccount,
        int expectedAccountVersion, int expectedCredentialVersion) {
        /** 隐藏注册和链接准备数据。 */
        @Override
        public String toString() {
            return "IssuePlan[REDACTED]";
        }
    }

    public record LinkProof(long challengeId, long accountId, int expectedVersion, ClientChallengePurpose purpose,
        String computedHmac, int keyVersion, String newPasswordVerifier) {
        /** 隐藏挑战和密码证明。 */
        @Override
        public String toString() {
            return "LinkProof[REDACTED]";
        }
    }
}
