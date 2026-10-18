package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientSessionContextType;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsSessionStatus;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientSessionRules.SessionState;

/** CUSTOMER/CLIENT 会话的本库查询投影。 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.experimental.Accessors(fluent = true)
public class ClientSession {
    /** 主键。 */
    private long id;

    /** 所属账号编号。 */
    private long accountId;

    /** 会话上下文类型。 */
    private ClientSessionContextType contextType;

    /** 租户编号；账号上下文为空。 */
    private Long tenantId;

    /** 成员编号；账号上下文为空。 */
    private Long memberId;

    /** 签发时的账号安全纪元。 */
    private int accountEpoch;

    /** 凭据版本。 */
    private int credentialVersion;

    /** 签发时间。 */
    private Instant issuedTime;

    /** 最近活动时间。 */
    private Instant lastActivityTime;

    /** 空闲到期时间。 */
    private Instant idleExpireTime;

    /** 绝对到期时间。 */
    private Instant absoluteExpireTime;

    /** 刷新代号。 */
    private int generationNo;

    /** 当前状态。 */
    private OperationsSessionStatus status;

    /** 并发版本。 */
    private int version;

    /** 投影当前会话不变量，不携带令牌。 */
    public SessionState state() {
        return new SessionState(status.getCode(), accountEpoch, credentialVersion, idleExpireTime, absoluteExpireTime);
    }
    /** 避免输出会话坐标。 */
    @Override public String toString() { return "ClientSession[REDACTED]"; }
}
