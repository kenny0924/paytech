package com.paytech.risk.decision.domainservice.service;

import com.paytech.risk.decision.domain.release.CreateTestReleaseContext;
import com.paytech.risk.decision.domain.release.ValidateTestReleaseContext;

/** 发布各本地原子阶段；加入调用方短事务，不读取或调用其他 Service。 */
public interface DecisionReleaseService {
    /** 创建根及初始审计。 */
    void create(CreateTestReleaseContext context);
    /** 更新根并记录开始审计。 */
    void startValidation(ValidateTestReleaseContext context);
    /** 保存完整八项结果及必要的拒绝状态。 */
    void completeValidation(ValidateTestReleaseContext context);
}
