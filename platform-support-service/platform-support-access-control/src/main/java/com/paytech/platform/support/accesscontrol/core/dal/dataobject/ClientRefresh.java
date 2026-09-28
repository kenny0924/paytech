package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.domain.enums.OperationsRefreshStatus;

/** Client Refresh 的本库查询投影。 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.experimental.Accessors(fluent = true)
public class ClientRefresh {
    /** 主键。 */
    private long id;

    /** 所属会话编号。 */
    private long sessionId;

    /** 刷新代号。 */
    private int generationNo;

    /** 公开刷新查找段。 */
    private String lookupId;

    /** 秘密 HMAC。 */
    private String secretHmac;

    /** HMAC 密钥版本。 */
    private int keyVersion;

    /** 当前状态。 */
    private OperationsRefreshStatus status;

    /** 到期时间。 */
    private Instant expiresTime;

    @Override
    public String toString() {
        return "ClientRefresh[protected projection]";
    }
}
