package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import com.paytech.platform.support.accesscontrol.domain.enums.OperationsRefreshStatus;

/** Operations 本库事实；由 Repository 在写入成功后同步原对象。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public class OperationsRefresh {
    /** Refresh代主键。 */
    private long id;

    /** 所属Session。 */
    private long sessionId;

    /** 单调递增代号。 */
    private int generationNo;

    /** 公开随机查询段。 */
    private String lookupId;

    /** Secret的域分隔HMAC，禁止日志。 */
    private String secretHmac;

    /** HMAC版本。 */
    private int keyVersion;

    /** 单次使用状态。 */
    private OperationsRefreshStatus status;

    /** 截止时间。 */
    private Instant expiresTime;

    /** 避免认证材料进入普通日志。 */
    @Override
    public String toString() { return "OperationsRefresh[REDACTED]"; }
}
