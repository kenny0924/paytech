package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.domain.authentication.ClientEmailChallengePolicy.ChallengeState;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengePurpose;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengeStatus;

/** 邀请接受挑战（t_authentication_challenge 中 TENANT_INVITATION_ACCEPT 用途）；不绑定账号，只保存 HMAC。 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.experimental.Accessors(fluent = true)
public class InvitationChallenge {
    /** 主键。 */
    private long id;

    /** 公开挑战查找编号。 */
    private String challengeNo;

    /** 所属邀请。 */
    private long invitationId;

    /** 受邀邮箱 Vault Token。 */
    private String emailToken;

    /** 受邀邮箱脱敏值。 */
    private String emailMasked;

    /** 挑战范围摘要（按邀请计算）。 */
    private String scopeHash;

    /** 秘密 HMAC。 */
    private String secretHmac;

    /** HMAC 密钥版本。 */
    private int keyVersion;

    /** 到期时间，等于邀请到期时间。 */
    private Instant expiresTime;

    /** 最大尝试次数，固定 5。 */
    private int maxAttempts;

    /** 已用尝试次数。 */
    private int usedAttempts;

    /** 当前状态。 */
    private ClientChallengeStatus status;

    /** 被替代的挑战；首发为空。 */
    private Long supersedesId;

    /** 并发版本。 */
    private int version;

    /** 创建时间。 */
    private Instant createTime;

    /** 转换为纯协议校验输入。 */
    public ChallengeState state() {
        return new ChallengeState(ClientChallengePurpose.TENANT_INVITATION_ACCEPT, status, secretHmac, keyVersion,
            expiresTime, maxAttempts, usedAttempts);
    }

    /** 不输出邮箱引用或摘要。 */
    @Override
    public String toString() {
        return "InvitationChallenge[REDACTED]";
    }
}
