package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import com.paytech.platform.support.api.enums.OperationsAccountStatus;
import com.paytech.platform.support.api.enums.OperationsAccountType;

/** Operations 本库事实；由 Repository 在写入成功后同步原对象。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public class OperationsAccount {
    /** 账号主键。 */
    private long id;

    /** 人工账号或一次性初始化账号。 */
    private OperationsAccountType type;

    /** 邮箱稳定 Vault Token，禁止外显。 */
    private String emailToken;

    /** 批准的邮箱脱敏展示。 */
    private String emailMasked;

    /** 账号当前生命周期。 */
    private OperationsAccountStatus status;

    /** 连续失败次数。 */
    private int failures;

    /** 临时锁定截止时间，可空。 */
    private Instant lockedUntil;

    /** 当前凭据版本。 */
    private int credentialVersion;

    /** 账号安全撤销纪元。 */
    private int authEpoch;

    /** 乐观并发版本。 */
    private int version;

    /** 避免认证材料进入普通日志。 */
    @Override
    public String toString() { return "OperationsAccount[REDACTED]"; }
}
