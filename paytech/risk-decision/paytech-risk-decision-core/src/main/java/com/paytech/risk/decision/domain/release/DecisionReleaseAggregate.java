package com.paytech.risk.decision.domain.release;

import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.StaticCheckCode;
import com.paytech.risk.decision.domain.enums.StaticCheckSeverity;
import com.paytech.risk.decision.domain.enums.StaticCheckStatus;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator.DecisionReleaseClosure;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** TEST Candidate Root、冻结组合与不可变审计证据的完整闭包。 */
public record DecisionReleaseAggregate(
    ReleaseRootView release,
    FrozenCompositionView frozenComposition,
    List<ReleaseStateChange> stateChanges,
    List<StaticCheckEvidence> staticChecks,
    DecisionReleaseClosure validationClosure
) {

    private static final List<StaticCheckCode> CHECK_ORDER = List.of(
        StaticCheckCode.REFERENCE_INTEGRITY,
        StaticCheckCode.AST_SHAPE,
        StaticCheckCode.AST_TYPES,
        StaticCheckCode.FEATURE_DEPENDENCY,
        StaticCheckCode.HANDLER_HASH,
        StaticCheckCode.ACTION_PARAMETERS,
        StaticCheckCode.TERMINAL_ACTION,
        StaticCheckCode.SYSTEM_OBLIGATION
    );

    public DecisionReleaseAggregate {
        Objects.requireNonNull(release, "release");
        Objects.requireNonNull(frozenComposition, "frozenComposition");
        Objects.requireNonNull(validationClosure, "validationClosure");
        stateChanges = immutable("stateChanges", stateChanges);
        staticChecks = immutable("staticChecks", staticChecks);
        validateCoordinates(release, frozenComposition);
        validateEvidence(release, stateChanges, staticChecks);
    }

    /**
     * 根据完整 Check 证据派生三态结果；零 Check 表示尚未完成静态校验。
     *
     * @return null、true 或 false
     */
    public Boolean allStaticChecksPassed() {
        if (staticChecks.isEmpty()) {
            return null;
        }
        return staticChecks.stream().allMatch(value -> value.status() == StaticCheckStatus.PASSED);
    }

    /**
     * 首期没有 Shadow 执行能力；只有静态校验全过的 VALIDATING Candidate 表示等待后续 Shadow 能力。
     *
     * @return 是否等待未来 Shadow 阶段
     */
    public boolean waitingForShadow() {
        return release.status() == DecisionReleaseStatus.VALIDATING
            && Boolean.TRUE.equals(allStaticChecksPassed());
    }

    private static void validateCoordinates(ReleaseRootView root, FrozenCompositionView composition) {
        requirePositive(root.releaseId(), "releaseId");
        requirePositive(root.releaseNo(), "releaseNo");
        requirePositive(root.decisionPointId(), "decisionPointId");
        requirePositive(root.pointVersionId(), "pointVersionId");
        requirePositive(root.ruleSetVersionId(), "ruleSetVersionId");
        requirePositive(root.createdBy(), "createdBy");
        Objects.requireNonNull(root.createTime(), "createTime");
        Objects.requireNonNull(root.status(), "status");
        if (root.environment() != DecisionEnvironment.TEST) {
            throw invalid("首期 Candidate 必须属于 TEST 环境");
        }
        requireHash(root.contentHash(), "releaseContentHash");
        if (root.decisionPointId() != composition.decisionPointId()
            || root.pointVersionId() != composition.pointVersionId()
            || root.ruleSetVersionId() != composition.ruleSetVersionId()
            || !root.pointCode().equals(composition.pointCode())) {
            throw invalid("Release 冻结组合坐标不一致");
        }
        requirePositive(composition.pointVersionNo(), "pointVersionNo");
        requirePositive(composition.ruleSetVersionNo(), "ruleSetVersionNo");
        requireHash(composition.pointVersionContentHash(), "pointVersionContentHash");
        requireHash(composition.ruleSetContentHash(), "ruleSetContentHash");
    }

    private static void validateEvidence(
        ReleaseRootView root,
        List<ReleaseStateChange> events,
        List<StaticCheckEvidence> checks
    ) {
        validateStateChanges(root, events);
        validateChecks(checks);
        boolean allPassed = !checks.isEmpty()
            && checks.stream().allMatch(value -> value.status() == StaticCheckStatus.PASSED);
        switch (root.status()) {
            case DRAFT -> requireShape(root.version() == 1 && events.size() == 1 && checks.isEmpty(),
                "DRAFT Release 证据形态无效");
            case VALIDATING -> requireShape(root.version() == 2 && events.size() == 2
                && (checks.isEmpty() || checks.size() == CHECK_ORDER.size() && allPassed),
                "VALIDATING Release 证据形态无效");
            case REJECTED -> requireShape(root.version() == 3 && events.size() == 3
                && checks.size() == CHECK_ORDER.size() && !allPassed,
                "REJECTED Release 证据形态无效");
            default -> throw invalid("首期 Candidate 状态不可见");
        }
    }

    private static void validateStateChanges(ReleaseRootView root, List<ReleaseStateChange> events) {
        if (events.isEmpty() || events.size() > 3) {
            throw invalid("Release State Change 数量无效");
        }
        Set<Long> ids = new HashSet<>();
        Instant previousTime = null;
        for (int index = 0; index < events.size(); index++) {
            ReleaseStateChange event = events.get(index);
            requirePositive(event.auditEventId(), "auditEventId");
            requirePositive(event.aggregateVersion(), "aggregateVersion");
            if (event.actorId() == null) {
                throw invalid("actorId 无效");
            }
            requirePositive(event.actorId(), "actorId");
            if (!ids.add(event.auditEventId()) || event.aggregateVersion() != index + 1) {
                throw invalid("Release State Change 顺序无效");
            }
            Objects.requireNonNull(event.eventTime(), "eventTime");
            if (previousTime != null && event.eventTime().isBefore(previousTime)) {
                throw invalid("Release State Change 时间顺序无效");
            }
            previousTime = event.eventTime();
            if (!"OPERATIONS_COMMAND".equals(event.triggerType())
                || event.triggerId() == null || !event.triggerId().matches("[0-9a-f]{64}")) {
                throw invalid("Release State Change Trigger 无效");
            }
        }
        ReleaseStateChange initial = events.getFirst();
        requireShape(initial.fromStatus() == null && initial.toStatus() == DecisionReleaseStatus.DRAFT
            && "CANDIDATE_CREATED".equals(initial.reasonCode()), "Candidate 初始事件无效");
        if (events.size() >= 2) {
            ReleaseStateChange started = events.get(1);
            requireShape(started.fromStatus() == DecisionReleaseStatus.DRAFT
                && started.toStatus() == DecisionReleaseStatus.VALIDATING
                && "STATIC_VALIDATION_STARTED".equals(started.reasonCode()), "静态校验开始事件无效");
        }
        if (events.size() == 3) {
            ReleaseStateChange started = events.get(1);
            ReleaseStateChange rejected = events.get(2);
            requireShape(rejected.fromStatus() == DecisionReleaseStatus.VALIDATING
                && rejected.toStatus() == DecisionReleaseStatus.REJECTED
                && "STATIC_VALIDATION_FAILED".equals(rejected.reasonCode())
                && rejected.triggerId().equals(started.triggerId())
                && rejected.actorId().equals(started.actorId()), "静态校验失败事件无效");
        }
        requireShape(events.getLast().toStatus() == root.status(), "Release Root 与事件状态不一致");
    }

    private static void validateChecks(List<StaticCheckEvidence> checks) {
        if (!checks.isEmpty() && checks.size() != CHECK_ORDER.size()) {
            throw invalid("Static Check 必须为零条或固定八条");
        }
        Set<Long> ids = new HashSet<>();
        for (int index = 0; index < checks.size(); index++) {
            StaticCheckEvidence check = checks.get(index);
            requirePositive(check.auditEventId(), "auditEventId");
            Objects.requireNonNull(check.eventTime(), "eventTime");
            if (!ids.add(check.auditEventId()) || check.checkCode() != CHECK_ORDER.get(index)) {
                throw invalid("Static Check 顺序或标识无效");
            }
            if (check.status() == StaticCheckStatus.PASSED) {
                requireShape(check.severity() == StaticCheckSeverity.INFO
                    && check.errorCode() == null && check.errorSummary() == null,
                    "PASSED Static Check 字段矩阵无效");
            } else if (check.status() == StaticCheckStatus.FAILED) {
                requireShape(check.severity() == StaticCheckSeverity.ERROR
                    && StaticCheckErrorSummary.isCanonical(check.errorCode(), check.errorSummary()),
                    "FAILED Static Check 字段矩阵无效");
            } else {
                throw invalid("Static Check 状态无效");
            }
        }
    }

    private static void requireHash(String value, String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw invalid(name + " 无效");
        }
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw invalid(name + " 无效");
        }
    }

    private static void requireShape(boolean condition, String message) {
        if (!condition) {
            throw invalid(message);
        }
    }

    private static <T> List<T> immutable(String name, List<T> values) {
        if (values == null || values.stream().anyMatch(Objects::isNull)) {
            throw invalid(name + " 无效");
        }
        return List.copyOf(values);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    public record ReleaseRootView(
        long releaseId,
        int releaseNo,
        long decisionPointId,
        String pointCode,
        long pointVersionId,
        long ruleSetVersionId,
        DecisionEnvironment environment,
        DecisionReleaseStatus status,
        int version,
        String contentHash,
        long createdBy,
        Instant createTime
    ) {
    }

    public record FrozenCompositionView(
        long decisionPointId,
        String pointCode,
        long pointVersionId,
        int pointVersionNo,
        String pointVersionContentHash,
        long ruleSetVersionId,
        String ruleSetCode,
        int ruleSetVersionNo,
        String ruleSetContentHash
    ) {
    }

    public record ReleaseStateChange(
        long auditEventId,
        Instant eventTime,
        DecisionReleaseStatus fromStatus,
        DecisionReleaseStatus toStatus,
        String reasonCode,
        String triggerType,
        String triggerId,
        Long actorId,
        int aggregateVersion
    ) {
    }

    public record StaticCheckEvidence(
        long auditEventId,
        Instant eventTime,
        StaticCheckCode checkCode,
        StaticCheckStatus status,
        StaticCheckSeverity severity,
        String errorCode,
        String errorSummary
    ) {
    }
}
