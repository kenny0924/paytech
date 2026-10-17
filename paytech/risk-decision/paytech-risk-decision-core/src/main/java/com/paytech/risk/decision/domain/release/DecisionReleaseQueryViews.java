package com.paytech.risk.decision.domain.release;

import java.util.List;
import java.util.Objects;

import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointRuleSet;
import com.paytech.risk.decision.core.dal.dataobject.DecisionRelease;
import com.paytech.risk.decision.core.dal.dataobject.ReleaseAuditEvent;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository.DecisionRuleSetAggregate;

/** 多表读取的发布事实，用于快照核验和响应组装。 */
public final class DecisionReleaseQueryViews {
    private DecisionReleaseQueryViews() { }

    /** 发布分页的本地查询条件。 */
    public record ReleasePageQuery(String pointCode, DecisionReleaseStatus status, int limit, int offset) {
    }

    /** 一个发布的列表组合视图。 */
    public record ReleaseSummaryView(
        /** 实际发布根。 */
        DecisionRelease release,
        /** 实际所属决策点。 */
        DecisionPoint decisionPoint,
        /** 准确版本绑定。 */
        DecisionPointRuleSet pointRuleSet,
        /** 八项结果的派生状态；未完成为 null。 */
        Boolean allStaticChecksPassed
    ) {
        public ReleaseSummaryView {
            Objects.requireNonNull(release, "release");
            Objects.requireNonNull(decisionPoint, "decisionPoint");
            Objects.requireNonNull(pointRuleSet, "pointRuleSet");
        }
    }

    /** 发布列表的多表分页结果。 */
    public record ReleasePageView(List<ReleaseSummaryView> items, long total) {
        public ReleasePageView {
            items = List.copyOf(items);
        }
    }

    /** 事务间装载的不可变发布组成。 */
    public record FrozenReleaseCompositionView(
        /** 实际发布根。 */
        DecisionRelease release,
        /** 经过引用校验的准确冻结组成。 */
        DecisionRuleSetAggregate frozenComposition
    ) {
        public FrozenReleaseCompositionView {
            Objects.requireNonNull(release, "release");
            Objects.requireNonNull(frozenComposition, "frozenComposition");
        }
    }

    /** 实际发布根、冻结组成和完整审计的组合视图。 */
    public record ReleaseDetailView(
        /** 实际发布根。 */
        DecisionRelease release,
        /** 经过引用校验的准确冻结组成。 */
        DecisionRuleSetAggregate frozenComposition,
        /** 完整状态变更事实。 */
        List<ReleaseAuditEvent> stateChanges,
        /** 零条或完整八项静态审计事实。 */
        List<ReleaseAuditEvent> staticChecks
    ) {
        public ReleaseDetailView {
            Objects.requireNonNull(release, "release");
            Objects.requireNonNull(frozenComposition, "frozenComposition");
            stateChanges = List.copyOf(stateChanges);
            staticChecks = List.copyOf(staticChecks);
        }
    }
}
