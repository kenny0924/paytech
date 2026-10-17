package com.paytech.risk.decision.domain.release;

import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.core.dal.dataobject.DecisionRelease;
import com.paytech.risk.decision.core.dal.dataobject.ReleaseAuditEvent;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetVersion;
import com.paytech.risk.decision.domain.release.DecisionReleaseResult.CreatedCandidate;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已核验的新 Candidate 与初始审计，贯穿原子保存和响应。 */
@Getter
@AllArgsConstructor
public class CreateTestReleaseContext {
    /** 待保存的发布根。 */
    private final DecisionRelease release;
    /** 已锁定且核验的所属决策点。 */
    private final DecisionPoint point;
    /** 已核验的准确冻结规则版本。 */
    private final RuleSetVersion ruleSetVersion;
    /** 与根同事务写入的初始状态事实。 */
    private final ReleaseAuditEvent initialStateChange;

    /** 从同一批已保存事实构造响应，不重新查询。 */
    public CreatedCandidate result() {
        return new CreatedCandidate(release.releaseId(), release.canonicalReleaseNo(), release.decisionPointId(),
            point.pointCode(), release.pointVersionId(), ruleSetVersion.ruleSetVersionId(), release.environmentCode(),
            release.status(), release.version(), release.contentHash(), release.createdBy(), release.createTime(),
            DecisionReleaseAssembler.toStateChange(initialStateChange));
    }
}
