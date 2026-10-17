package com.paytech.risk.decision.service.query;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToLongFunction;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointRuleSet;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointVersion;
import com.paytech.risk.decision.core.dal.dataobject.DecisionRelease;
import com.paytech.risk.decision.core.dal.dataobject.ReleaseAuditEvent;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetVersion;
import com.paytech.risk.decision.domain.enums.DecisionPointVersionStatus;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.ReleaseAuditEventType;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domain.enums.StaticCheckCode;
import com.paytech.risk.decision.domain.enums.StaticCheckSeverity;
import com.paytech.risk.decision.domain.enums.StaticCheckStatus;
import com.paytech.risk.decision.domain.release.DecisionReleaseQueryViews.FrozenReleaseCompositionView;
import com.paytech.risk.decision.domain.release.DecisionReleaseQueryViews.ReleaseDetailView;
import com.paytech.risk.decision.domain.release.DecisionReleaseQueryViews.ReleasePageQuery;
import com.paytech.risk.decision.domain.release.DecisionReleaseQueryViews.ReleasePageView;
import com.paytech.risk.decision.domain.release.DecisionReleaseQueryViews.ReleaseSummaryView;
import com.paytech.risk.decision.domain.release.StaticCheckErrorSummary;
import com.paytech.risk.decision.domainservice.repository.DecisionPointRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionPointRuleSetRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionPointVersionRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionReleaseRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository.DecisionRuleSetAggregate;
import com.paytech.risk.decision.domainservice.repository.ReleaseAuditEventRepository;
import com.paytech.risk.decision.domainservice.repository.RuleSetVersionRepository;

import lombok.AllArgsConstructor;

/** 发布多表查询协作者；事务由入口 Processor 控制，只通过 Owner Repository 读取。 */
@Component
@AllArgsConstructor
public class DecisionReleaseReader {
    private static final String CHECKOUT_PREPARE = "CHECKOUT_PREPARE";
    private static final String OPERATIONS_COMMAND = "OPERATIONS_COMMAND";
    private static final String CANDIDATE_CREATED = "CANDIDATE_CREATED";
    private static final String VALIDATION_STARTED = "STATIC_VALIDATION_STARTED";
    private static final String VALIDATION_FAILED = "STATIC_VALIDATION_FAILED";

    private static final List<String> STATIC_CHECK_ORDER = List.of(
        StaticCheckCode.REFERENCE_INTEGRITY.getCode(),
        StaticCheckCode.AST_SHAPE.getCode(),
        StaticCheckCode.AST_TYPES.getCode(),
        StaticCheckCode.FEATURE_DEPENDENCY.getCode(),
        StaticCheckCode.HANDLER_HASH.getCode(),
        StaticCheckCode.ACTION_PARAMETERS.getCode(),
        StaticCheckCode.TERMINAL_ACTION.getCode(),
        StaticCheckCode.SYSTEM_OBLIGATION.getCode()
    );

    private static final Map<String, Integer> STATIC_CHECK_POSITIONS = checkPositions();

    private final DecisionPointRepository points;
    private final RuleSetVersionRepository ruleVersions;
    private final DecisionReleaseRepository roots;
    private final DecisionRuleSetRepository ruleSetRepository;
    private final ReleaseAuditEventRepository audits;
    private final DecisionPointRuleSetRepository bindings;
    private final DecisionPointVersionRepository pointVersions;

    /** 使用行锁得到的同一个根，避免重复加载丢失对象身份。 */
    public ReleaseDetailView requireClosure(DecisionRelease release) {
        List<ReleaseAuditEvent> audits = safe(
            this.audits.findByRelease(release.releaseId()), "Release Audit Event");
        AuditView auditSnapshot = validateAudits(release, audits);

        DecisionRuleSetAggregate composition = requireFrozenComposition(release);
        return new ReleaseDetailView(
            release, composition, auditSnapshot.stateChanges(), auditSnapshot.staticChecks());
    }

    /** 组合已加载根的本地只读事实。 */
    public FrozenReleaseCompositionView requireFrozenComposition(long releaseId) {
        DecisionRelease release = requireFound(roots.findRoot(releaseId).orElse(null),
            "Decision Release 不存在: releaseId=" + releaseId);
        return new FrozenReleaseCompositionView(release, requireFrozenComposition(release));
    }

    /** 组合已加载根的本地只读事实。 */
    private DecisionRuleSetAggregate requireFrozenComposition(DecisionRelease release) {
        DecisionPointVersion pointVersion = this.pointVersions.findById(release.pointVersionId()).orElse(null);
        if (pointVersion == null || pointVersion.pointVersionId() != release.pointVersionId()
            || pointVersion.decisionPointId() != release.decisionPointId()) {
            throw corruption("Release 引用的 Point Version 缺失或跨 Decision Point");
        }
        List<DecisionPointRuleSet> bindings = safe(
            this.bindings.findByPointVersion(pointVersion.pointVersionId()), "Release Point Rule Set 绑定");
        uniqueIds(bindings, DecisionPointRuleSet::bindingId, "Release Point Rule Set 绑定");
        if (bindings.size() != 1) {
            throw corruption("Release 的 Point Version 必须恰好绑定一个 Rule Set Version");
        }
        DecisionPointRuleSet binding = bindings.getFirst();
        if (binding.pointVersionId() != pointVersion.pointVersionId()) {
            throw corruption("Release 的 Point Rule Set 绑定跨 Point Version");
        }

        DecisionRuleSetAggregate composition = ruleSetRepository.requireClosure(binding.ruleSetVersionId());
        validateFrozenComposition(release, pointVersion, binding, composition);
        return composition;
    }

    /** 核验发布查询的引用、完整性和审计矩阵。 */
    public ReleasePageView findPage(ReleasePageQuery query) {
        Objects.requireNonNull(query, "query");
        requirePage(query.limit(), query.offset());
        List<DecisionRelease> releases = safe(roots.findPage(
            query.pointCode(), query.status(), query.limit(), query.offset()), "Release 分页");
        long total = roots.count(query.pointCode(), query.status());
        if (releases.isEmpty()) {
            return new ReleasePageView(List.of(), total);
        }

        Map<Long, DecisionRelease> releasesById = uniqueIds(
            releases, DecisionRelease::releaseId, "Release 分页");
        LinkedHashSet<Long> pointIds = new LinkedHashSet<>();
        LinkedHashSet<Long> pointVersionIds = new LinkedHashSet<>();
        for (DecisionRelease release : releases) {
            validateFirstSliceRoot(release);
            pointIds.add(release.decisionPointId());
            pointVersionIds.add(release.pointVersionId());
        }

        List<DecisionPoint> points = safe(
            this.points.findByIds(List.copyOf(pointIds)), "Release 分页 Decision Point");
        Map<Long, DecisionPoint> pointsById = uniqueIds(points, DecisionPoint::decisionPointId,
            "Release 分页 Decision Point");
        requireExactIds(pointIds, pointsById.keySet(), "Release 分页 Decision Point");

        List<DecisionPointRuleSet> bindings = safe(
            this.bindings.findByPointVersionIds(List.copyOf(pointVersionIds)),
            "Release 分页 Rule Set 绑定");
        uniqueIds(bindings, DecisionPointRuleSet::bindingId, "Release 分页 Rule Set 绑定");
        Map<Long, List<DecisionPointRuleSet>> bindingsByPointVersion = group(
            bindings, DecisionPointRuleSet::pointVersionId);
        if (bindings.isEmpty()) {
            throw corruption("Release 分页 Rule Set 绑定缺失");
        }

        List<DecisionPointVersion> pointVersions = safe(
            this.pointVersions.findByIds(List.copyOf(pointVersionIds)), "Release 分页 Point Version");
        Map<Long, DecisionPointVersion> pointVersionsById = uniqueIds(pointVersions,
            DecisionPointVersion::pointVersionId, "Release 分页 Point Version");
        requireExactIds(pointVersionIds, pointVersionsById.keySet(), "Release 分页 Point Version");

        LinkedHashSet<Long> ruleSetVersionIds = bindings.stream().map(DecisionPointRuleSet::ruleSetVersionId)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<RuleSetVersion> ruleSetVersions = safe(
            ruleVersions.findByIds(List.copyOf(ruleSetVersionIds)),
            "Release 分页 Rule Set Version");
        Map<Long, RuleSetVersion> ruleSetVersionsById = uniqueIds(ruleSetVersions,
            RuleSetVersion::ruleSetVersionId, "Release 分页 Rule Set Version");
        requireExactIds(ruleSetVersionIds, ruleSetVersionsById.keySet(), "Release 分页 Rule Set Version");

        List<ReleaseAuditEvent> audits = safe(this.audits.findByReleases(
            List.copyOf(releasesById.keySet())), "Release 分页审计事件");
        uniqueIds(audits, ReleaseAuditEvent::auditEventId, "Release 分页审计事件");
        Map<Long, List<ReleaseAuditEvent>> auditsByRelease = group(audits, ReleaseAuditEvent::releaseId);

        List<ReleaseSummaryView> items = new ArrayList<>(releases.size());
        for (DecisionRelease release : releases) {
            DecisionPoint point = pointsById.get(release.decisionPointId());
            List<DecisionPointRuleSet> releaseBindings = bindingsByPointVersion.getOrDefault(
                release.pointVersionId(), List.of());
            if (point == null || !CHECKOUT_PREPARE.equals(point.pointCode()) || releaseBindings.size() != 1) {
                throw corruption("Release 分页闭包缺失唯一 CHECKOUT_PREPARE Point 或 Rule Set 绑定");
            }
            DecisionPointRuleSet binding = releaseBindings.getFirst();
            DecisionPointVersion pointVersion = pointVersionsById.get(release.pointVersionId());
            RuleSetVersion ruleSetVersion = ruleSetVersionsById.get(binding.ruleSetVersionId());
            if (binding.pointVersionId() != release.pointVersionId() || binding.sequenceNo() != 1
                || pointVersion == null || pointVersion.decisionPointId() != release.decisionPointId()
                || pointVersion.status() != DecisionPointVersionStatus.FROZEN
                || ruleSetVersion == null || ruleSetVersion.decisionPointId() != release.decisionPointId()
                || ruleSetVersion.status() != RuleSetVersionStatus.FROZEN) {
                throw corruption("Release 分页冻结 Point/Rule Set 绑定坐标或状态损坏");
            }
            AuditView snapshot = validateAudits(release,
                auditsByRelease.getOrDefault(release.releaseId(), List.of()));
            Boolean allPassed = snapshot.staticChecks().isEmpty() ? null
                : snapshot.staticChecks().stream()
                    .allMatch(value -> value.checkStatus() == StaticCheckStatus.PASSED);
            items.add(new ReleaseSummaryView(release, point, binding, allPassed));
        }
        return new ReleasePageView(items, total);
    }

    /** 核验发布查询的引用、完整性和审计矩阵。 */
    private void validateFrozenComposition(
        DecisionRelease release,
        DecisionPointVersion pointVersion,
        DecisionPointRuleSet binding,
        DecisionRuleSetAggregate composition
    ) {
        if (release.decisionPointId() != composition.decisionPoint().decisionPointId()
            || release.pointVersionId() != composition.pointVersion().pointVersionId()
            || pointVersion.pointVersionId() != composition.pointVersion().pointVersionId()
            || binding.ruleSetVersionId() != composition.ruleSetVersion().ruleSetVersionId()) {
            throw corruption("Release 冻结组成坐标错配");
        }
        if (!CHECKOUT_PREPARE.equals(composition.decisionPoint().pointCode())
            || composition.pointVersion().status() != DecisionPointVersionStatus.FROZEN
            || composition.ruleSetVersion().status() != RuleSetVersionStatus.FROZEN) {
            throw corruption("Release 只允许引用冻结的 CHECKOUT_PREPARE 组成");
        }
        if (!composition.pointFeatures().isEmpty() || !composition.featureVersions().isEmpty()
            || !composition.featureDependencies().isEmpty()
            || !composition.actionParameterDefinitions().isEmpty()
            || !composition.ruleActionParameters().isEmpty()
            || !composition.pointObligations().isEmpty()) {
            throw corruption("首期 Release 组成不得包含 Feature、Parameter 或 Obligation");
        }
    }

    /** 核验发布查询的引用、完整性和审计矩阵。 */
    private AuditView validateAudits(DecisionRelease release, List<ReleaseAuditEvent> audits) {
        validateFirstSliceRoot(release);
        uniqueIds(audits, ReleaseAuditEvent::auditEventId, "Release Audit Event");
        List<ReleaseAuditEvent> stateChanges = new ArrayList<>();
        List<ReleaseAuditEvent> staticChecks = new ArrayList<>();
        for (ReleaseAuditEvent event : audits) {
            if (event.releaseId() != release.releaseId()) {
                throw corruption("Audit Event 引用了其他 Release");
            }
            if (event.shadowRunId() != null || event.eventType() == ReleaseAuditEventType.APPROVAL) {
                throw corruption("首期 TEST Candidate 出现 Shadow 或 Approval 审计事实");
            }
            switch (event.eventType()) {
                case STATE_CHANGE -> {
                    validateStateChangeMatrix(event);
                    stateChanges.add(event);
                }
                case STATIC_CHECK -> {
                    validateStaticCheck(event);
                    staticChecks.add(event);
                }
                case APPROVAL -> throw corruption("首期 TEST Candidate 不接受 Approval 审计事实");
            }
        }

        stateChanges.sort(Comparator
            .comparing(ReleaseAuditEvent::aggregateVersion)
            .thenComparingLong(ReleaseAuditEvent::auditEventId));
        if (staticChecks.size() != 0 && staticChecks.size() != STATIC_CHECK_ORDER.size()) {
            throw corruption("Static Check 必须为零条或完整八条");
        }
        staticChecks.sort(Comparator
            .comparingInt((ReleaseAuditEvent value) -> STATIC_CHECK_POSITIONS.get(value.checkCode()))
            .thenComparingLong(ReleaseAuditEvent::auditEventId));
        for (int index = 0; index < staticChecks.size(); index++) {
            if (!STATIC_CHECK_ORDER.get(index).equals(staticChecks.get(index).checkCode())) {
                throw corruption("Static Check 缺失、重复或顺序无法规范化");
            }
        }
        validateStateChain(release, stateChanges, staticChecks);
        return new AuditView(List.copyOf(stateChanges), List.copyOf(staticChecks));
    }

    /** 核验发布查询的引用、完整性和审计矩阵。 */
    private void validateStaticCheck(ReleaseAuditEvent event) {
        require(event.eventType() == ReleaseAuditEventType.STATIC_CHECK, "审计事件不是 STATIC_CHECK");
        require(STATIC_CHECK_POSITIONS.containsKey(event.checkCode()), "Static Check Code 未知");
        require(event.checkStatus() != null && event.severity() != null,
            "Static Check 缺少状态或严重度");
        require(event.shadowRunId() == null && event.approvalRequestId() == null
            && event.approvalStatus() == null && event.reviewerId() == null
            && event.reviewComment() == null && event.fromStatus() == null && event.toStatus() == null
            && event.reasonCode() == null && event.triggerType() == null && event.triggerId() == null
            && event.actorId() == null && event.aggregateVersion() == null,
            "Static Check 混入 State、Approval 或 Shadow 字段");
        if (event.checkStatus() == StaticCheckStatus.PASSED) {
            require(event.severity() == StaticCheckSeverity.INFO
                && event.errorCode() == null && event.errorSummary() == null,
                "PASSED Static Check 必须为 INFO 且没有错误字段");
        } else {
            require(event.checkStatus() == StaticCheckStatus.FAILED
                && event.severity() == StaticCheckSeverity.ERROR
                && StaticCheckErrorSummary.isCanonical(event.errorCode(), event.errorSummary()),
                "FAILED Static Check 必须为 ERROR 且包含错误字段");
        }
    }

    /** 核验发布查询的引用、完整性和审计矩阵。 */
    private void validateStateChangeMatrix(ReleaseAuditEvent event) {
        require(event.eventType() == ReleaseAuditEventType.STATE_CHANGE, "审计事件不是 STATE_CHANGE");
        require(event.checkCode() == null && event.checkStatus() == null && event.severity() == null
            && event.errorCode() == null && event.errorSummary() == null && event.shadowRunId() == null
            && event.approvalRequestId() == null && event.approvalStatus() == null
            && event.reviewerId() == null && event.reviewComment() == null,
            "State Change 混入 Static Check、Approval 或 Shadow 字段");
        require(event.toStatus() != null && isNonBlank(event.reasonCode())
            && OPERATIONS_COMMAND.equals(event.triggerType()) && isNonBlank(event.triggerId())
            && event.actorId() != null && event.aggregateVersion() != null
            && event.aggregateVersion() > 0,
            "State Change 缺少目标状态、稳定原因、命令触发信息、Actor 或聚合版本");
    }

    /** 核验发布查询的引用、完整性和审计矩阵。 */
    private void validateStateChain(
        DecisionRelease release,
        List<ReleaseAuditEvent> stateChanges,
        List<ReleaseAuditEvent> staticChecks
    ) {
        int expectedStates = switch (release.status()) {
            case DRAFT -> 1;
            case VALIDATING -> 2;
            case REJECTED -> 3;
            default -> throw corruption("首期 TEST Candidate 出现未批准状态");
        };
        int expectedVersion = expectedStates;
        if (release.version() != expectedVersion || stateChanges.size() != expectedStates) {
            throw corruption("Release Root Version 与 State Change 链不一致");
        }
        requireTransition(stateChanges.get(0), null, DecisionReleaseStatus.DRAFT, CANDIDATE_CREATED, 1);
        if (expectedStates >= 2) {
            requireTransition(stateChanges.get(1), DecisionReleaseStatus.DRAFT,
                DecisionReleaseStatus.VALIDATING, VALIDATION_STARTED, 2);
        }
        if (expectedStates == 3) {
            requireTransition(stateChanges.get(2), DecisionReleaseStatus.VALIDATING,
                DecisionReleaseStatus.REJECTED, VALIDATION_FAILED, 3);
        }
        if (release.status() == DecisionReleaseStatus.DRAFT && !staticChecks.isEmpty()) {
            throw corruption("DRAFT Candidate 不得已有 Static Check");
        }
        if (release.status() == DecisionReleaseStatus.REJECTED
            && (staticChecks.size() != STATIC_CHECK_ORDER.size()
                || staticChecks.stream().noneMatch(value -> value.checkStatus() == StaticCheckStatus.FAILED))) {
            throw corruption("REJECTED Candidate 必须有完整八项检查且至少一项失败");
        }
        if (release.status() == DecisionReleaseStatus.VALIDATING && !staticChecks.isEmpty()
            && staticChecks.stream().anyMatch(value -> value.checkStatus() != StaticCheckStatus.PASSED)) {
            throw corruption("留在 VALIDATING 的完整八项检查必须全部通过");
        }
    }

    /** 核验发布查询的引用、完整性和审计矩阵。 */
    private void validateFirstSliceRoot(DecisionRelease release) {
        release.verifyIntegrity();
    }

    /** 组合已加载根的本地只读事实。 */
    private static void requireTransition(
        ReleaseAuditEvent event,
        DecisionReleaseStatus fromStatus,
        DecisionReleaseStatus toStatus,
        String reasonCode,
        int version
    ) {
        if (event.fromStatus() != fromStatus || event.toStatus() != toStatus
            || !reasonCode.equals(event.reasonCode()) || event.aggregateVersion() != version) {
            throw corruption("Release State Change 链不是批准的首期顺序");
        }
    }

    /** 核验发布查询的引用、完整性和审计矩阵。 */
    private static Map<String, Integer> checkPositions() {
        LinkedHashMap<String, Integer> positions = new LinkedHashMap<>();
        for (int index = 0; index < STATIC_CHECK_ORDER.size(); index++) {
            positions.put(STATIC_CHECK_ORDER.get(index), index);
        }
        return Map.copyOf(positions);
    }

    /** 核验发布查询的引用、完整性和审计矩阵。 */
    private static <T> Map<Long, T> uniqueIds(List<T> values, ToLongFunction<T> id, String label) {
        LinkedHashMap<Long, T> indexed = new LinkedHashMap<>();
        for (T value : values) {
            if (value == null) {
                throw corruption(label + " 包含 null 行");
            }
            long key = id.applyAsLong(value);
            if (indexed.putIfAbsent(key, value) != null) {
                throw corruption(label + " 包含重复主键");
            }
        }
        return Map.copyOf(indexed);
    }

    /** 核验发布查询的引用、完整性和审计矩阵。 */
    private static <T> Map<Long, List<T>> group(List<T> values, ToLongFunction<T> key) {
        Map<Long, List<T>> grouped = new HashMap<>();
        for (T value : values) {
            grouped.computeIfAbsent(key.applyAsLong(value), ignored -> new ArrayList<>()).add(value);
        }
        grouped.replaceAll((ignored, list) -> List.copyOf(list));
        return Map.copyOf(grouped);
    }

    /** 组合已加载根的本地只读事实。 */
    private static void requireExactIds(Set<Long> requested, Set<Long> loaded, String label) {
        if (!requested.equals(loaded)) {
            throw corruption(label + " 查询结果缺失或包含额外主键");
        }
    }

    /** 组合已加载根的本地只读事实。 */
    private static <T> T requireFound(T value, String message) {
        if (value == null) {
            throw new NoSuchElementException(message);
        }
        return value;
    }

    /** 核验发布查询的引用、完整性和审计矩阵。 */
    private static <T> List<T> safe(List<T> values, String label) {
        if (values == null) {
            throw corruption(label + " Mapper 返回 null");
        }
        return List.copyOf(values);
    }

    /** 组合已加载根的本地只读事实。 */
    private static void requirePage(int limit, int offset) {
        if (limit <= 0 || offset < 0) {
            throw new IllegalArgumentException("分页 limit/offset 非法");
        }
    }

    /** 组合已加载根的本地只读事实。 */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw corruption(message);
        }
    }

    /** 核验发布查询的引用、完整性和审计矩阵。 */
    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    /** 核验发布查询的引用、完整性和审计矩阵。 */
    private static ErrorException corruption(String message) {
        return new ErrorException(message);
    }

    private record AuditView(
        List<ReleaseAuditEvent> stateChanges,
        List<ReleaseAuditEvent> staticChecks
    ) {
    }
}
