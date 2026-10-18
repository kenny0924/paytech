package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;

/** CUSTOMER Realm 人工账号的本库投影，复用全局账号的现行四种状态代码。 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.experimental.Accessors(fluent = true)
public class CustomerAccount {
    /** 主键。 */
    private long id;

    /** 邮箱 Vault 引用。 */
    private String emailToken;

    /** 脱敏邮箱。 */
    private String emailMasked;

    /** 邮箱验证时间。 */
    private Instant verifiedTime;

    /** 当前状态。 */
    private OperationsAccountStatus status;

    /** 凭据版本。 */
    private int credentialVersion;

    /** 账号安全纪元。 */
    private int authEpoch;

    /** 并发版本。 */
    private int version;

    /** 连续密码失败次数。 */
    private int failedAttempts;

    /** 账号临时锁定截止时间。 */
    private Instant lockedUntil;

    /** 不暴露邮箱 Token 或安全状态。 */
    @Override public String toString() { return "CustomerAccount[REDACTED]"; }
}
