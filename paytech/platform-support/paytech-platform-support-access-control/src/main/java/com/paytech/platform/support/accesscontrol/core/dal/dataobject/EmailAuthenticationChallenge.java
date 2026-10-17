package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengePurpose;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengeStatus;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientEmailChallengePolicy.ChallengeState;

/** CUSTOMER 注册/密码恢复挑战投影；数据库只保留不可逆 HMAC。 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.experimental.Accessors(fluent = true)
public class EmailAuthenticationChallenge {
    /** 主键。 */
    private long id;

    /** 公开挑战查找编号。 */
    private String challengeNo;

    /** 所属账号编号。 */
    private long accountId;

    /** 挑战用途。 */
    private ClientChallengePurpose purpose;

    /** 邮箱 Vault 引用。 */
    private String emailToken;

    /** 脱敏邮箱。 */
    private String emailMasked;

    /** 挑战作用域摘要。 */
    private String scopeHash;

    /** 秘密 HMAC。 */
    private String secretHmac;

    /** HMAC 密钥版本。 */
    private int keyVersion;

    /** 到期时间。 */
    private Instant expiresTime;

    /** 最大尝试次数。 */
    private int maxAttempts;

    /** 已用尝试次数。 */
    private int usedAttempts;

    /** 当前状态。 */
    private ClientChallengeStatus status;

    /** 被替代挑战编号。 */
    private Long supersedesId;

    /** 并发版本。 */
    private int version;

    /** 创建时间。 */
    private Instant createTime;

    /** 转换为不包含账号资料的纯协议校验输入。 */
    public ChallengeState state() {
        return new ChallengeState(purpose, status, secretHmac, keyVersion, expiresTime, maxAttempts, usedAttempts);
    }
    /** 不暴露账号邮箱引用或挑战摘要。 */
    @Override public String toString() { return "EmailAuthenticationChallenge[REDACTED]"; }
}
