package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import com.paytech.platform.support.api.enums.OperationsContextType;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsSessionStatus;

/** Operations 本库事实；由 Repository 在写入成功后同步原对象。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public class OperationsSession {
    /** 会话主键。 */
    private long id;

    /** 会话账号。 */
    private long accountId;

    /** 固定Bootstrap或INTERNAL Tenant上下文。 */
    private OperationsContextType contextType;

    /** Bootstrap为空。 */
    private Long tenantId;

    /** Bootstrap为空。 */
    private Long memberId;

    /** 签发时安全纪元。 */
    private int accountEpoch;

    /** 签发时凭据版本。 */
    private int credentialVersion;

    /** 会话创建UTC时刻。 */
    private Instant issuedTime;

    /** 最近刷新UTC时刻。 */
    private Instant lastActivityTime;

    /** 空闲截止。 */
    private Instant idleExpireTime;

    /** 绝对截止，不可延长。 */
    private Instant absoluteExpireTime;

    /** 当前Refresh代号。 */
    private int generationNo;

    /** 会话状态。 */
    private OperationsSessionStatus status;

    /** 避免认证材料进入普通日志。 */
    @Override
    public String toString() { return "OperationsSession[REDACTED]"; }
}
