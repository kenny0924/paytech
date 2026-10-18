package com.paytech.risk.decision.domain.rule;

import static com.paytech.risk.decision.domainservice.policy.RuleSetManagementPolicy.requireConfigurable;
import static com.paytech.risk.decision.domainservice.policy.RuleSetManagementPolicy.requireIncrementable;
import java.time.Instant;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointVersion;
import com.paytech.risk.decision.core.dal.dataobject.RuleSet;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetVersion;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domainservice.service.DecisionRuleSetService.FrozenRuleSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 冻结用例持有已锁定的根、规则版本和可选旧协议原对象。 */
@Getter
@AllArgsConstructor
public class FreezeRuleSetVersionContext {
    /** 已锁定的整体根。 */
    private final RuleSet ruleSet;

    /** 已核验内容 Hash 和冻结资格的版本。 */
    private final RuleSetVersion ruleSetVersion;

    /** 已锁定的旧协议；普通新配置不存在。 */
    private final DecisionPointVersion companion;

    /** 可信冻结操作者。 */
    private final long actorAccountId;

    /** 本次命令 UTC 微秒时间。 */
    private final Instant commandTime;

    /** 根与版本的关联和递增上限在写入前确认。 */
    public void verifyIntegrity() {
        requireConfigurable(ruleSet);
        ruleSet.verifyManagementHead(ruleSetVersion);
        requireIncrementable(ruleSet);
        if (ruleSetVersion.status() != RuleSetVersionStatus.DRAFT)
            throw new ErrorException("冻结上下文必须持有 DRAFT 版本");
    }

    /** 数据库 CAS 成功后从原对象返回冻结事实。 */
    public FrozenRuleSet result() {
        return new FrozenRuleSet(ruleSetVersion.ruleSetVersionId(), companion == null ? null : companion.pointVersionId(),
            ruleSetVersion.ruleSetCode(), ruleSetVersion.versionNo(), ruleSetVersion.status(), ruleSetVersion.contentHash(),
            ruleSetVersion.frozenBy(), ruleSetVersion.frozenTime());
    }
}
