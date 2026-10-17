package com.paytech.risk.decision.domainservice.service;

import com.paytech.risk.decision.domain.flow.DeleteFlowContext;
import com.paytech.risk.decision.domain.flow.SaveFlowContext;

/** 流程管理的本地原子保存能力，不重新查询资格或调用其他 Service。 */
public interface DecisionFlowManagementService {
    /** 在调用方事务内保存完整版本并切换原根指针。 */
    void save(SaveFlowContext context);
    /** 在调用方事务内写入整体删除事实，保留所有历史明细。 */
    void delete(DeleteFlowContext context);
}
