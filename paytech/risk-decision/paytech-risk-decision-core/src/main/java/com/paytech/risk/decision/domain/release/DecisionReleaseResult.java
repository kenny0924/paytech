package com.paytech.risk.decision.domain.release;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.release.DecisionReleaseAggregate.ReleaseStateChange;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator.StaticCheckResult;

/** 发布查询与写入的 Owner 结果，不暴露数据库对象。 */
public final class DecisionReleaseResult {
    private DecisionReleaseResult() { }

    /** 发布列表的多表分页结果。 */
    public record ReleasePageView(List<ReleaseSummaryView> items, int page, int size, long total) {
        public ReleasePageView {
            items = immutable("items", items);
        }
    }

    /** 一个发布的列表组合视图。 */
    public record ReleaseSummaryView(
        /** 发布稳定身份。 */
        long releaseId,
        /** 正整数发布编号。 */
        int releaseNo,
        /** 所属决策点身份。 */
        long decisionPointId,
        /** 决策点编码。 */
        String pointCode,
        /** 准确冻结的点版本身份。 */
        long pointVersionId,
        /** 准确冻结的规则版本身份。 */
        long ruleSetVersionId,
        /** 发布环境。 */
        DecisionEnvironment environment,
        /** 持久化发布状态。 */
        DecisionReleaseStatus status,
        /** 发布聚合版本。 */
        int version,
        /** 冻结组成的内容哈希。 */
        String contentHash,
        /** 创建人身份。 */
        long createdBy,
        /** 创建 UTC 时刻。 */
        Instant createTime,
        /** 八项结果的派生状态；未完成为 null。 */
        Boolean allStaticChecksPassed
    ) {
    }

    /** 新建发布成功后的最小 Owner 结果。 */
    public record CreatedCandidate(
        /** 发布稳定身份。 */
        long releaseId,
        /** 正整数发布编号。 */
        int releaseNo,
        /** 所属决策点身份。 */
        long decisionPointId,
        /** 决策点编码。 */
        String pointCode,
        /** 准确冻结的点版本身份。 */
        long pointVersionId,
        /** 准确冻结的规则版本身份。 */
        long ruleSetVersionId,
        /** 发布环境。 */
        DecisionEnvironment environment,
        /** 持久化发布状态。 */
        DecisionReleaseStatus status,
        /** 发布聚合版本。 */
        int version,
        /** 冻结组成的内容哈希。 */
        String contentHash,
        /** 创建人身份。 */
        long createdBy,
        /** 创建 UTC 时刻。 */
        Instant createTime,
        /** 与初始根同事务保存的状态事实。 */
        ReleaseStateChange initialStateChange
    ) {
        public CreatedCandidate {
            Objects.requireNonNull(initialStateChange, "initialStateChange");
        }
    }

    /** 发布校验完成后的八项证据结果。 */
    public record ValidatedCandidate(
        /** 发布稳定身份。 */
        long releaseId,
        /** 正整数发布编号。 */
        int releaseNo,
        /** 发布环境。 */
        DecisionEnvironment environment,
        /** 持久化发布状态。 */
        DecisionReleaseStatus status,
        /** 发布聚合版本。 */
        int version,
        /** 冻结组成的内容哈希。 */
        String contentHash,
        /** 八项结果的派生状态；未完成为 null。 */
        boolean allStaticChecksPassed,
        /** 固定顺序的完整八项检查结果。 */
        List<StaticCheckResult> checks
    ) {
        public ValidatedCandidate {
            checks = immutable("checks", checks);
        }
    }

    private static <T> List<T> immutable(String name, List<T> values) {
        if (values == null || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(name + " 无效");
        }
        return List.copyOf(values);
    }
}
