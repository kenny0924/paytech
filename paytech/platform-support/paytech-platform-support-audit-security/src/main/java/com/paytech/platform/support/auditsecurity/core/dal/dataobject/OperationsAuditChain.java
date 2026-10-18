package com.paytech.platform.support.auditsecurity.core.dal.dataobject;

import com.paytech.platform.support.auditsecurity.domain.enums.OperationsAuditChainStatus;

/** Operations AuditChain 的本库查询投影。 */
public record OperationsAuditChain(
    /** 审计链主键。 */
    long id,
    /** 末事件序号。 */
    int sequence,
    /** 末事件Hash，可空。 */
    String lastHash, /** 链当前状态。 */
    OperationsAuditChainStatus status) {
    @Override
    public String toString() {
        return "OperationsAuditChain[protected projection]";
    }
}
