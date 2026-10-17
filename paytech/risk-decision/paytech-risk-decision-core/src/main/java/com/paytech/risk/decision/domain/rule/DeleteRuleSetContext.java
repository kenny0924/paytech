package com.paytech.risk.decision.domain.rule;

import static com.paytech.risk.decision.domainservice.policy.RuleSetManagementPolicy.requireConfigurable;
import static com.paytech.risk.decision.domainservice.policy.RuleSetManagementPolicy.requireIncrementable;
import java.time.Instant;
import com.paytech.risk.decision.core.dal.dataobject.RuleSet;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetVersion;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService.DeletedRuleSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 整体删除上下文；保留历史版本，只改变管理根。 */
@Getter
@AllArgsConstructor
public class DeleteRuleSetContext {
    /** 已锁定并确认没有活动发布引用的根。 */
    private final RuleSet ruleSet;

    /** 同一根当前准确版本。 */
    private final RuleSetVersion currentVersion;

    /** 可信删除操作者。 */
    private final long actorAccountId;

    /** 本次删除 UTC 微秒时间。 */
    private final Instant commandTime;

    /** 校验调用方的管理版本，不重新查询引用。 */
    public void verifyIntegrity(long expectedVersion) {
        requireConfigurable(ruleSet);
        ruleSet.verifyManagementHead(currentVersion);
        ruleSet.verifyExpectedVersion(expectedVersion);
        requireIncrementable(ruleSet);
    }

    /** 从不可变删除事实返回结果。 */
    public DeletedRuleSet result() {
        return new DeletedRuleSet(ruleSet.getRuleSetId(), ruleSet.getVersion(), ruleSet.getDeletedBy(), ruleSet.getDeletedTime());
    }
}
