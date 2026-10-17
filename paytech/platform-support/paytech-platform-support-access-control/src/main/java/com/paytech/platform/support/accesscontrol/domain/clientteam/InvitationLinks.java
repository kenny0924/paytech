package com.paytech.platform.support.accesscontrol.domain.clientteam;

import com.paytech.platform.support.accesscontrol.domain.authentication.ClientEmailChallengePolicy.LinkMaterial;

/** 邀请链接与邮箱在本次调用中的事务外准备结果；不持久化原值。 */
public final class InvitationLinks {

    private InvitationLinks() {}

    /**
     * 受邀邮箱：规范原值只用于本次发信，持久化只用 Token 与脱敏值
     *
     * @param canonical 规范邮箱原值
     * @param token 稳定 Vault Token
     * @param masked 脱敏值
     */
    public record InvitationEmail(String canonical, String token, String masked) {
        /** 不输出邮箱原值或 Token。 */
        @Override
        public String toString() {
            return "InvitationEmail[REDACTED]";
        }
    }

    /**
     * 新签发的链接：秘密只在本次调用与邮件中出现，数据库只保存 HMAC
     *
     * @param challengeId 预先分配的挑战主键
     * @param link 链接原值
     * @param scopeHash 按邀请计算的范围摘要
     * @param secretHmac 秘密 HMAC
     * @param keyVersion HMAC 密钥版本
     */
    public record IssuedLink(long challengeId, LinkMaterial link, String scopeHash, String secretHmac, int keyVersion) {
        /** 不输出链接秘密。 */
        @Override
        public String toString() {
            return "IssuedLink[REDACTED]";
        }
    }

    /**
     * 链接证明的事务外核对结果；事务内仍需锁定挑战并按状态重证
     *
     * @param challengeId 挑战
     * @param invitationId 邀请
     * @param secretMatches 秘密是否正确（已排除被替代与尝试用尽）
     */
    public record InvitationProof(long challengeId, long invitationId, boolean secretMatches) {
    }
}
