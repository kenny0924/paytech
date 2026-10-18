package com.paytech.risk.decision.domain.flow;

import java.time.Instant;

import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowRow;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已完成根完整性和修订号校验的整体删除上下文。 */
@Getter
@AllArgsConstructor
public class DeleteFlowContext {
    /** 事务内按主键锁定的原始根。 */
    private final DecisionFlowRow root;
    /** 可信操作者 Account ID。 */
    private final long actorAccountId;
    /** 本次删除的 UTC 微秒时间。 */
    private final Instant deletedTime;

    /** 从 SQL 成功后同步的根返回不可变删除事实。 */
    public DecisionFlow.Deleted result() {
        return new DecisionFlow.Deleted(root.flowId(), root.version(), root.deletedBy(), root.deletedTime());
    }
}
