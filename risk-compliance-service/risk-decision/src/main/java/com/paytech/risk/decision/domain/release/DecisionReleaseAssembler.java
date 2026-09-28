package com.paytech.risk.decision.domain.release;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.decision.core.dal.dataobject.ActionVersion;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPoint;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointInputField;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointRuleSet;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointVersion;
import com.paytech.risk.decision.core.dal.dataobject.DecisionRelease;
import com.paytech.risk.decision.core.dal.dataobject.ReleaseAuditEvent;
import com.paytech.risk.decision.core.dal.dataobject.RuleSetVersion;
import com.paytech.risk.decision.domain.authoring.CheckoutPrepareAuthoringDescriptor;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry.ActionKey;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry.PersistedActionVersion;
import com.paytech.risk.decision.domain.enums.ConfigurationStatus;
import com.paytech.risk.decision.domain.enums.DecisionBusinessDomain;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionPointStatus;
import com.paytech.risk.decision.domain.enums.DecisionPointVersionStatus;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.ExecutionVersionStatus;
import com.paytech.risk.decision.domain.enums.ReleaseAuditEventType;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domain.enums.StaticCheckCode;
import com.paytech.risk.decision.domain.enums.StaticCheckSeverity;
import com.paytech.risk.decision.domain.enums.StaticCheckStatus;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.DecisionPointVersionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.DecisionReleaseProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.InputFieldProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseActionVersionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseDecisionPointProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseInputFieldProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseNodeProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseOperandProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleasePointVersionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseRuleActionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseRuleProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseRuleSetBindingProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.ReleaseRuleSetVersionProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.RuleSetBindingProjection;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.RuleSetVersionProjection;
import com.paytech.risk.decision.domain.release.DecisionReleaseAggregate;
import com.paytech.risk.decision.domain.release.DecisionReleaseAggregate.FrozenCompositionView;
import com.paytech.risk.decision.domain.release.DecisionReleaseAggregate.ReleaseRootView;
import com.paytech.risk.decision.domain.release.DecisionReleaseAggregate.ReleaseStateChange;
import com.paytech.risk.decision.domain.release.DecisionReleaseAggregate.StaticCheckEvidence;
import com.paytech.risk.decision.domain.release.DecisionReleaseResult.*;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator.DecisionReleaseClosure;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator.StaticCheckResult;
import com.paytech.risk.decision.domain.release.StaticCheckErrorSummary;
import com.paytech.risk.decision.domainservice.repository.DecisionRuleSetRepository;

import lombok.AllArgsConstructor;

/** 已加载发布事实的纯组装器，保持既有内容 Hash 字节与审计合同。 */
@Component
@AllArgsConstructor
public class DecisionReleaseAssembler {
    private static final String CHECKOUT_PREPARE = "CHECKOUT_PREPARE";
    private static final String OPERATIONS_COMMAND = "OPERATIONS_COMMAND";
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
    private static final DateTimeFormatter UTC_MICROSECOND = new DateTimeFormatterBuilder()
        .appendPattern("uuuu-MM-dd'T'HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 6, 6, true)
        .appendLiteral('Z')
        .toFormatter()
        .withZone(ZoneOffset.UTC);

    private final DecisionActionRegistry registry;
    private final RiskDecisionCanonicalizer canonicalizer;
    private final CheckoutPrepareAuthoringDescriptor descriptor;

    /** 从批量读取的发布根、决策点和绑定派生列表项。 */
    public ReleaseSummaryView summary(DecisionReleaseQueryViews.ReleaseSummaryView stored) {
        if (stored == null) {
            throw corruption("Release 分页含 null 行");
        }
        DecisionRelease release = stored.release();
        DecisionPoint point = stored.decisionPoint();
        DecisionPointRuleSet binding = stored.pointRuleSet();
        release.verifyIntegrity();
        if (point.decisionPointId() != release.decisionPointId()
            || !CHECKOUT_PREPARE.equals(point.pointCode())
            || binding.pointVersionId() != release.pointVersionId()) {
            throw corruption("Release 分页坐标或 Point 目录损坏");
        }
        Boolean passed = stored.allStaticChecksPassed();
        if (release.status() == DecisionReleaseStatus.DRAFT && passed != null
            || release.status() == DecisionReleaseStatus.VALIDATING && Boolean.FALSE.equals(passed)
            || release.status() == DecisionReleaseStatus.REJECTED && !Boolean.FALSE.equals(passed)) {
            throw corruption("Release 分页 Static Check 派生状态损坏");
        }
        return new ReleaseSummaryView(release.releaseId(), release.canonicalReleaseNo(), release.decisionPointId(),
            point.pointCode(), release.pointVersionId(), binding.ruleSetVersionId(), release.environmentCode(),
            release.status(), release.version(), release.contentHash(), release.createdBy(), release.createTime(),
            passed);
    }

    /** 从已读取的冻结组成核验内容哈希与完整审计矩阵。 */
    public DecisionReleaseAggregate domainAggregate(DecisionReleaseQueryViews.ReleaseDetailView stored, RuleSetVersionProjection ruleProjection) {
        try {
            if (stored == null) {
                throw corruption("Release Repository 返回 null 闭包");
            }
            DecisionRelease release = stored.release();
            release.verifyIntegrity();
            FrozenRelease frozenRelease = frozenRelease(release, stored.frozenComposition(), ruleProjection);
            FrozenBundle frozen = frozenRelease.frozen();
            DecisionRuleSetRepository.DecisionRuleSetAggregate composition = frozenRelease.composition();
            DecisionPointRuleSet binding = frozenRelease.binding();
            ReleaseRootView root = new ReleaseRootView(release.releaseId(), release.canonicalReleaseNo(),
                release.decisionPointId(), composition.decisionPoint().pointCode(), release.pointVersionId(),
                binding.ruleSetVersionId(), release.environmentCode(), release.status(), release.version(),
                release.contentHash(), release.createdBy(), release.createTime());
            FrozenCompositionView frozenView = new FrozenCompositionView(release.decisionPointId(),
                composition.decisionPoint().pointCode(), release.pointVersionId(),
                composition.pointVersion().versionNo(), frozen.pointHash(), binding.ruleSetVersionId(),
                composition.ruleSetVersion().ruleSetCode(), composition.ruleSetVersion().versionNo(),
                frozen.ruleSetHash());
            List<ReleaseStateChange> states = stored.stateChanges().stream().map(DecisionReleaseAssembler::toStateChange).toList();
            List<StaticCheckEvidence> checks = stored.staticChecks().stream().map(this::toCheckEvidence).toList();
            return new DecisionReleaseAggregate(root, frozenView, states, checks, frozenRelease.closure());
        } catch (ErrorException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw corruption("Release 持久化闭包损坏", exception);
        }
    }

    /** 组合准确冻结内容与当次 Action 目录，供事务外八项检查使用。 */
    public DecisionReleaseClosure validationClosure(
        DecisionReleaseQueryViews.FrozenReleaseCompositionView stored, RuleSetVersionProjection ruleProjection,
        List<PersistedActionVersion> persistedActions
    ) {
        try {
            if (stored == null) {
                throw corruption("Release Repository 返回 null 冻结组成");
            }
            return frozenRelease(stored.release(), stored.frozenComposition(), persistedActions, ruleProjection).closure();
        } catch (ErrorException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw corruption("Release 冻结组成损坏", exception);
        }
    }

    /** 核验发布引用坐标和原 Release 内容哈希。 */
    private FrozenRelease frozenRelease(
        DecisionRelease release,
        DecisionRuleSetRepository.DecisionRuleSetAggregate composition, RuleSetVersionProjection ruleProjection
    ) {
        return frozenRelease(release, composition, historicalReferencedActionEvidence(composition), ruleProjection);
    }

    /** 核验发布引用坐标和原 Release 内容哈希。 */
    private FrozenRelease frozenRelease(
        DecisionRelease release,
        DecisionRuleSetRepository.DecisionRuleSetAggregate composition,
        List<PersistedActionVersion> persistedActions, RuleSetVersionProjection ruleProjection
    ) {
        if (release == null || composition == null || release.environmentCode() != DecisionEnvironment.TEST
            || release.releaseId() <= 0 || release.decisionPointId() <= 0 || release.pointVersionId() <= 0
            || !hash(release.contentHash())) {
            throw corruption("Decision Release 不可变根坐标损坏");
        }
        int canonicalReleaseNo = release.canonicalReleaseNo();
        FrozenBundle frozen = frozenBundle(composition, false, persistedActions, ruleProjection);
        DecisionPointRuleSet binding = only(composition.pointRuleSets(), "Release Rule Set Binding");
        if (release.decisionPointId() != composition.decisionPoint().decisionPointId()
            || release.pointVersionId() != composition.pointVersion().pointVersionId()
            || binding.pointVersionId() != release.pointVersionId()
            || binding.ruleSetVersionId() != composition.ruleSetVersion().ruleSetVersionId()) {
            throw corruption("Release Root 与冻结组成坐标不一致");
        }
        DecisionReleaseProjection releaseProjection = releaseProjection(release.releaseId(), canonicalReleaseNo,
            composition, frozen.pointHash(), frozen.ruleSetHash());
        String recomputedReleaseHash = canonicalHash(() -> canonicalizer.decisionRelease(releaseProjection),
            "Release 内容");
        if (!recomputedReleaseHash.equals(release.contentHash())) {
            throw corruption("Release 内容哈希与冻结闭包不一致");
        }
        DecisionReleaseClosure closure = new DecisionReleaseClosure(
            frozen.pointProjection(), frozen.ruleSetProjection(), releaseProjection, frozen.persistedActions());
        return new FrozenRelease(composition, binding, frozen, closure);
    }

    /** 校验冻结配置的资格与规范内容，不访问外部依赖。 */
    public FrozenBundle frozenBundle(
        DecisionRuleSetRepository.DecisionRuleSetAggregate composition,
        boolean businessRejection,
        List<PersistedActionVersion> persistedActions, RuleSetVersionProjection ruleSetProjection
    ) {
        try {
            Objects.requireNonNull(composition, "composition");
            DecisionPoint point = composition.decisionPoint();
            DecisionPointVersion pointVersion = composition.pointVersion();
            RuleSetVersion ruleSet = composition.ruleSetVersion();
            boolean supported = CHECKOUT_PREPARE.equals(point.pointCode())
                && point.businessDomain() == DecisionBusinessDomain.CHECKOUT
                && (!businessRejection || point.status() == DecisionPointStatus.ACTIVE)
                && pointVersion.status() == DecisionPointVersionStatus.FROZEN
                && ruleSet.status() == RuleSetVersionStatus.FROZEN
                && composition.pointRuleSets().size() == 1
                && composition.pointFeatures().isEmpty()
                && composition.featureVersions().isEmpty()
                && composition.featureDependencies().isEmpty()
                && composition.actionParameterDefinitions().isEmpty()
                && composition.ruleActionParameters().isEmpty()
                && composition.pointObligations().isEmpty();
            if (!supported) {
                if (businessRejection) {
                    throw new BizCheckFailException("首期 Candidate 只接受冻结的 CHECKOUT_PREPARE 单绑定基础闭包");
                }
                throw corruption("Release 引用的冻结闭包不符合首期合同");
            }
            requireDescriptorPoint(composition);
            String ruleSetHash = canonicalHash(() -> canonicalizer.ruleSetVersion(ruleSetProjection),
                "Rule Set Version 内容");
            if (!ruleSetHash.equals(ruleSet.contentHash())) {
                throw corruption("Rule Set Version 内容哈希漂移");
            }
            DecisionPointVersionProjection pointProjection = pointProjection(composition, ruleSetHash);
            String pointHash = canonicalHash(() -> canonicalizer.decisionPointVersion(pointProjection),
                "Decision Point Version 内容");
            if (!pointHash.equals(pointVersion.contentHash())) {
                throw corruption("Decision Point Version 内容哈希漂移");
            }
            return new FrozenBundle(pointProjection, ruleSetProjection, pointHash, ruleSetHash, persistedActions);
        } catch (BizCheckFailException | ErrorException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw corruption("冻结 Rule Set 闭包损坏", exception);
        }
    }

    /** 从原字段快照还原物理 ID，保持历史 Release 规范字节不变。 */
    private static String legacyInputFieldId(String fieldCode,
        DecisionRuleSetRepository.DecisionRuleSetAggregate composition) {
        if (fieldCode == null) return null;
        var matches = composition.inputFields().stream().filter(field -> field.fieldCode().equals(fieldCode)
            && field.pointVersionId() == composition.pointVersion().pointVersionId()).toList();
        if (matches.size() != 1) throw corruption("旧发布输入字段无法唯一还原");
        return id(matches.getFirst().inputFieldId());
    }

    /** 还原原 Point Version 内容投影，保持历史 Hash 合同。 */
    private DecisionPointVersionProjection pointProjection(
        DecisionRuleSetRepository.DecisionRuleSetAggregate composition,
        String ruleSetHash
    ) {
        DecisionPoint point = composition.decisionPoint();
        DecisionPointVersion version = composition.pointVersion();
        RuleSetVersion ruleSet = composition.ruleSetVersion();
        DecisionPointRuleSet binding = only(composition.pointRuleSets(), "Point Rule Set Binding");
        List<InputFieldProjection> fields = composition.inputFields().stream().map(value -> new InputFieldProjection(
            value.fieldCode(), value.valueType(), value.requiredFlag(), value.dataClassification(),
            value.ruleReferenceFlag(), value.actionParameterFlag())).toList();
        return new DecisionPointVersionProjection(point.pointCode(), point.businessDomain(), version.versionNo(),
            version.inputContractCode(), version.inputContractVersion(), version.outputContractCode(),
            version.defaultDecision(), version.evaluationStrategy(), version.failureStrategy(), version.timeoutMillis(),
            fields, List.of(new RuleSetBindingProjection(binding.sequenceNo(), ruleSet.ruleSetCode(),
                ruleSet.versionNo(), ruleSetHash)), List.of(), List.of(), List.of());
    }

    /** 按原顺序还原冻结发布的完整规范投影。 */
    public DecisionReleaseProjection releaseProjection(
        long releaseId,
        int releaseNo,
        DecisionRuleSetRepository.DecisionRuleSetAggregate composition,
        String pointHash,
        String ruleSetHash
    ) {
        DecisionPoint point = composition.decisionPoint();
        DecisionPointVersion pointVersion = composition.pointVersion();
        RuleSetVersion ruleSet = composition.ruleSetVersion();
        DecisionPointRuleSet binding = only(composition.pointRuleSets(), "Release Rule Set Binding");
        List<ReleaseInputFieldProjection> fields = composition.inputFields().stream().map(value ->
            new ReleaseInputFieldProjection(id(value.inputFieldId()), id(value.pointVersionId()), value.fieldCode(),
                value.valueType(), value.requiredFlag(), value.dataClassification(), value.ruleReferenceFlag(),
                value.actionParameterFlag())).toList();
        List<ReleaseRuleProjection> rules = composition.rules().stream().map(value -> new ReleaseRuleProjection(
            id(value.ruleId()), id(value.ruleSetVersionId()), value.ruleCode(), value.priorityNo(), value.enabledFlag(),
            value.reasonCode(), value.reasonMessage())).toList();
        List<ReleaseNodeProjection> nodes = composition.nodes().stream().map(value -> new ReleaseNodeProjection(
            id(value.nodeId()), id(value.ruleId()), nullableId(value.parentNodeId()), value.nodeType(),
            value.groupOperator(), value.comparisonOperator(), value.sequenceNo())).toList();
        List<ReleaseOperandProjection> operands = composition.operands().stream().map(value ->
            new ReleaseOperandProjection(id(value.operandId()), id(value.nodeId()), value.operandRole(),
                value.valueSequence(), value.sourceType(), nullableId(value.featureVersionId()),
                legacyInputFieldId(value.fieldCode(), composition), value.valueType(), value.stringValue(),
                value.numberValue() == null ? null : value.numberValue().setScale(8, RoundingMode.UNNECESSARY).toPlainString(),
                value.booleanValue(), value.datetimeValue() == null ? null : UTC_MICROSECOND.format(value.datetimeValue()),
                value.durationValue() == null ? null : Long.toString(value.durationValue()))).toList();
        List<ReleaseActionVersionProjection> actions = composition.actionVersions().stream().map(value ->
            new ReleaseActionVersionProjection(id(value.actionVersionId()), value.actionCode(), value.actionCategory(),
                value.versionNo(), value.handlerCode(), value.implementationVersion(), value.implementationHash(),
                value.terminalFlag(), value.decisionCode(), value.mergePolicy(), value.shadowSupportedFlag())).toList();
        List<ReleaseRuleActionProjection> ruleActions = composition.ruleActions().stream().map(value ->
            new ReleaseRuleActionProjection(id(value.ruleActionId()), id(value.ruleId()), id(value.actionVersionId()),
                value.sequenceNo())).toList();
        return new DecisionReleaseProjection(id(releaseId), releaseNo, id(point.decisionPointId()),
            id(pointVersion.pointVersionId()), DecisionEnvironment.TEST,
            new ReleaseDecisionPointProjection(id(point.decisionPointId()), point.pointCode(), point.businessDomain()),
            new ReleasePointVersionProjection(id(point.decisionPointId()), point.pointCode(),
                id(pointVersion.pointVersionId()), pointVersion.versionNo(), pointVersion.inputContractCode(),
                pointVersion.inputContractVersion(), pointVersion.outputContractCode(), pointVersion.defaultDecision(),
                pointVersion.evaluationStrategy(), pointVersion.failureStrategy(), pointVersion.timeoutMillis(), pointHash),
            fields, List.of(new ReleaseRuleSetBindingProjection(id(binding.bindingId()), id(binding.pointVersionId()),
                id(binding.ruleSetVersionId()), binding.sequenceNo())),
            List.of(new ReleaseRuleSetVersionProjection(id(ruleSet.ruleSetVersionId()),
                id(ruleSet.decisionPointId()), ruleSet.ruleSetCode(), ruleSet.versionNo(), ruleSetHash, ruleSet.contentFormat())),
            rules, nodes, operands, actions, ruleActions, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /** 首次创建必须具备完整且 ACTIVE 的内置 Action 目录。 */
    public List<PersistedActionVersion> verifiedPersistedCatalog(List<PersistedActionVersion> persisted) {
        try {
            registry.verifyPersistedCatalog(persisted);
        } catch (RuntimeException exception) {
            throw corruption("持久化 Action 目录与内置 Handler 漂移", exception);
        }
        return persisted;
    }

    /** 历史读取只核对实际引用 Action，不受无关目录变化影响。 */
    private List<PersistedActionVersion> historicalReferencedActionEvidence(
        DecisionRuleSetRepository.DecisionRuleSetAggregate composition
    ) {
        return composition.actionVersions().stream().map(this::historicalReferencedAction).toList();
    }

    /** 核对历史 Action 稳定身份与实现哈希，允许已退休版本。 */
    private PersistedActionVersion historicalReferencedAction(ActionVersion value) {
        if (value.actionVersionId() <= 0
            || (value.status() != ExecutionVersionStatus.ACTIVE
                && value.status() != ExecutionVersionStatus.RETIRED)) {
            throw corruption("Action Version 历史引用状态或标识损坏");
        }
        try {
            var expected = registry.require(new ActionKey(value.actionCode(), value.versionNo()));
            PersistedActionVersion actual = persistedAction(value, ConfigurationStatus.ACTIVE);
            long expectedId = 240001L + descriptor.actions().indexOf(expected);
            PersistedActionVersion expectedRow = new PersistedActionVersion(expectedId,
                expected.actionCode(), expected.versionNo(), expected.handlerCode(),
                expected.implementationVersion(), expected.implementationHash(), expected.actionCategory(),
                expected.terminalFlag(), expected.decisionCode(), expected.mergePolicy(),
                expected.shadowSupportedFlag(), expected.status());
            if (!actual.equals(expectedRow) || !expected.actionName().equals(value.actionName())) {
                throw corruption("Release 引用 Action Version 与工程注册表不一致");
            }
            return actual;
        } catch (InvalidParameterException exception) {
            throw corruption("Release 引用 Action Version 不在工程注册表", exception);
        }
    }

    /** 将已读取的 Action 行转换为静态校验目录证据。 */
    public static PersistedActionVersion persistedAction(ActionVersion value, ConfigurationStatus status) {
        return new PersistedActionVersion(value.actionVersionId(), value.actionCode(), value.versionNo(),
            value.handlerCode(), value.implementationVersion(), value.implementationHash(),
            value.actionCategory(), value.terminalFlag(), value.decisionCode(), value.mergePolicy(),
            value.shadowSupportedFlag(), status);
    }

    /** 核验固定决策点协议与字段快照。 */
    private void requireDescriptorPoint(DecisionRuleSetRepository.DecisionRuleSetAggregate composition) {
        DecisionPoint point = composition.decisionPoint();
        DecisionPointVersion version = composition.pointVersion();
        CheckoutPrepareAuthoringDescriptor.DecisionPointDescriptor expected = descriptor.point();
        if (!expected.pointCode().equals(point.pointCode()) || !expected.pointName().equals(point.pointName())
            || !expected.businessDomain().equals(point.businessDomain().getCode())
            || !expected.inputContractCode().equals(version.inputContractCode())
            || expected.inputContractVersion() != version.inputContractVersion()
            || !expected.outputContractCode().equals(version.outputContractCode())
            || expected.defaultDecision() != version.defaultDecision()
            || expected.evaluationStrategy() != version.evaluationStrategy()
            || expected.failureStrategy() != version.failureStrategy()
            || expected.timeoutMillis() != version.timeoutMillis()
            || compositionFieldsInvalid(point, version)) {
            throw corruption("CHECKOUT_PREPARE Point 目录与内置合同漂移");
        }
        if (composition.inputFields().size() != descriptor.fields().size()) {
            throw corruption("CHECKOUT_PREPARE Input Field 目录数量漂移");
        }
        Map<String, DecisionPointInputField> actualFields = new HashMap<>();
        for (DecisionPointInputField field : composition.inputFields()) {
            if (field.pointVersionId() != version.pointVersionId()
                || actualFields.putIfAbsent(field.fieldCode(), field) != null) {
                throw corruption("CHECKOUT_PREPARE Input Field 目录坐标损坏");
            }
        }
        descriptor.fields().forEach(expectedField -> {
            DecisionPointInputField actual = actualFields.get(expectedField.fieldCode());
            if (actual == null || actual.valueType() != expectedField.valueType()
                || actual.requiredFlag() != expectedField.requiredFlag()
                || actual.dataClassification() != expectedField.dataClassification()
                || actual.ruleReferenceFlag() != expectedField.ruleReferenceFlag()
                || actual.actionParameterFlag() != expectedField.actionParameterFlag()) {
                throw corruption("CHECKOUT_PREPARE Input Field 目录内容漂移");
            }
        });
    }

    /** 判断决策点与版本的基本引用坐标。 */
    private static boolean compositionFieldsInvalid(DecisionPoint point, DecisionPointVersion version) {
        return point.decisionPointId() <= 0 || version.pointVersionId() <= 0
            || version.decisionPointId() != point.decisionPointId() || version.versionNo() <= 0;
    }

    /** 校验结果必须为固定顺序的八项，并满足通过或失败字段矩阵。 */
    public List<StaticCheckResult> requireExactChecks(List<StaticCheckResult> checks) {
        if (checks == null || checks.size() != CHECK_ORDER.size() || checks.stream().anyMatch(Objects::isNull)) {
            throw corruption("静态校验结果必须恰好包含固定八项");
        }
        for (int index = 0; index < CHECK_ORDER.size(); index++) {
            StaticCheckResult check = checks.get(index);
            if (check.checkCode() != CHECK_ORDER.get(index)) {
                throw corruption("静态校验结果顺序、重复或 Code 无效");
            }
            if (check.checkStatus() == StaticCheckStatus.PASSED) {
                if (check.severity() != StaticCheckSeverity.INFO
                    || check.errorCode() != null || check.errorSummary() != null) {
                    throw corruption("PASSED 静态校验字段矩阵无效");
                }
            } else if (check.checkStatus() == StaticCheckStatus.FAILED) {
                if (check.severity() != StaticCheckSeverity.ERROR || blank(check.errorCode())
                    || !StaticCheckErrorSummary.isCanonical(check.errorCode(), check.errorSummary())) {
                    throw corruption("FAILED 静态校验字段矩阵无效");
                }
            } else {
                throw corruption("静态校验状态无效");
            }
        }
        return List.copyOf(checks);
    }

    /** 构造不可变状态审计事实。 */
    public static ReleaseAuditEvent stateChange(
        long auditId,
        long releaseId,
        Instant time,
        DecisionReleaseStatus from,
        DecisionReleaseStatus to,
        String reason,
        String triggerId,
        long actor,
        int version
    ) {
        return new ReleaseAuditEvent(auditId, releaseId, ReleaseAuditEventType.STATE_CHANGE, time,
            null, null, null, null, null, null, null, null, null, null,
            from, to, reason, OPERATIONS_COMMAND, triggerId, actor, version, time);
    }

    /** 构造单项静态校验审计事实。 */
    public static ReleaseAuditEvent staticCheck(
        long auditId,
        long releaseId,
        Instant time,
        StaticCheckResult check
    ) {
        return new ReleaseAuditEvent(auditId, releaseId, ReleaseAuditEventType.STATIC_CHECK, time,
            check.checkCode().getCode(), check.checkStatus(), check.severity(), check.errorCode(),
            check.errorSummary(), null, null, null, null, null, null, null, null, null, null, null, null, time);
    }

    /** 转换状态审计为 Owner 不可变结果。 */
    public static ReleaseStateChange toStateChange(ReleaseAuditEvent event) {
        if (event.eventType() != ReleaseAuditEventType.STATE_CHANGE || event.aggregateVersion() == null) {
            throw corruption("State Change 审计字段矩阵损坏");
        }
        return new ReleaseStateChange(event.auditEventId(), event.eventTime(), event.fromStatus(), event.toStatus(),
            event.reasonCode(), event.triggerType(), event.triggerId(), event.actorId(), event.aggregateVersion());
    }

    /** 转换静态审计并拒绝未知检查编码。 */
    private StaticCheckEvidence toCheckEvidence(ReleaseAuditEvent event) {
        if (event.eventType() != ReleaseAuditEventType.STATIC_CHECK) {
            throw corruption("Static Check 审计字段矩阵损坏");
        }
        StaticCheckCode code;
        try {
            code = StaticCheckCode.valueOf(event.checkCode());
        } catch (RuntimeException exception) {
            throw corruption("Static Check Code 未知", exception);
        }
        return new StaticCheckEvidence(event.auditEventId(), event.eventTime(), code, event.checkStatus(),
            event.severity(), event.errorCode(), event.errorSummary());
    }

    /** 确认规则加锁前后业务坐标没有漂移。 */
    public static void requireSameRuleSetCoordinates(
        RuleSetVersion plain,
        RuleSetVersion current,
        RuleSetVersion target,
        DecisionPoint point
    ) {
        if (plain.ruleSetVersionId() != target.ruleSetVersionId()
            || plain.decisionPointId() != point.decisionPointId()
            || target.decisionPointId() != point.decisionPointId()
            || current.decisionPointId() != point.decisionPointId()
            || !plain.ruleSetCode().equals(target.ruleSetCode())
            || !plain.ruleSetCode().equals(current.ruleSetCode())) {
            throw corruption("Rule Set 锁后业务坐标漂移");
        }
    }

    /** 确认冻结组成引用同一决策点和目标规则版本。 */
    public static void requireClosureCoordinates(
        DecisionRuleSetRepository.DecisionRuleSetAggregate composition,
        DecisionPoint point,
        RuleSetVersion target
    ) {
        if (composition == null || composition.decisionPoint().decisionPointId() != point.decisionPointId()
            || composition.ruleSetVersion().ruleSetVersionId() != target.ruleSetVersionId()
            || composition.ruleSetVersion().decisionPointId() != point.decisionPointId()
            || composition.pointVersion().decisionPointId() != point.decisionPointId()) {
            throw corruption("Rule Set 闭包与锁定目标坐标不一致");
        }
    }

    /** 确认重载组成与锁定对象的不可变内容一致。 */
    public static void requireLockedComposition(
        DecisionPoint point,
        RuleSetVersion target,
        DecisionPointVersion companion,
        DecisionRuleSetRepository.DecisionRuleSetAggregate composition
    ) {
        if (!point.equals(composition.decisionPoint())
            || !target.equals(composition.ruleSetVersion())
            || !companion.equals(composition.pointVersion())) {
            throw corruption("锁定的 Point、Rule Set 或 Companion 与重载闭包不一致");
        }
    }

    /** 判断持久化 SHA-256 文本格式。 */
    private static boolean hash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    /** 以无前导零正整数文本生成规范投影身份。 */
    private static String id(long value) {
        if (value <= 0) {
            throw corruption("持久化标识不是正整数");
        }
        return Long.toString(value);
    }

    /** 可空引用保持原规范表示。 */
    private static String nullableId(Long value) {
        return value == null ? null : id(value);
    }

    /** 判断检查错误编码是否为空。 */
    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** 冻结发布必须恰好具有一个绑定。 */
    private static <T> T only(List<T> values, String label) {
        if (values == null || values.size() != 1 || values.getFirst() == null) {
            throw corruption(label + " 必须恰好一条");
        }
        return values.getFirst();
    }

    /** 将规范化失败转换为持久化内容损坏错误。 */
    public static String canonicalHash(Supplier<RiskDecisionCanonicalizer.CanonicalHash> operation, String label) {
        try {
            RiskDecisionCanonicalizer.CanonicalHash hash = operation.get();
            if (hash == null || !DecisionReleaseAssembler.hash(hash.sha256LowerHex())) {
                throw corruption(label + "规范哈希结果无效");
            }
            return hash.sha256LowerHex();
        } catch (ErrorException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw corruption(label + "无法规范化", exception);
        }
    }

    /** 构造不泄露规则内容的完整性错误。 */
    private static ErrorException corruption(String message) {
        return new ErrorException(message);
    }

    /** 构造不泄露规则内容的完整性错误。 */
    private static ErrorException corruption(String message, Throwable cause) {
        return new ErrorException(message, cause);
    }

    public record FrozenBundle(
        DecisionPointVersionProjection pointProjection,
        RuleSetVersionProjection ruleSetProjection,
        String pointHash,
        String ruleSetHash,
        List<PersistedActionVersion> persistedActions
    ) { }

    private record FrozenRelease(
        DecisionRuleSetRepository.DecisionRuleSetAggregate composition,
        DecisionPointRuleSet binding,
        FrozenBundle frozen,
        DecisionReleaseClosure closure
    ) { }
}
