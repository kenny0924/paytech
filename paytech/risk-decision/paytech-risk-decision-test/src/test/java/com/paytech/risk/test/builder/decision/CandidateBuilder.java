package com.paytech.risk.test.builder.decision;

import static com.paytech.risk.test.builder.decision.RuleSetBuilder.ALLOW_ACTION_VERSION_ID;
import static com.paytech.risk.test.builder.decision.RuleSetBuilder.DECISION_POINT_ID;
import static com.paytech.risk.test.builder.decision.RuleSetBuilder.MINIMAL_RULE;
import static com.paytech.risk.test.builder.decision.RuleSetBuilder.POINT_CODE;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.enums.*;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer.*;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizerImpl;

/**
 * TEST Candidate 及其旧发布组成的测试 Builder
 * <li>组成：一个 FROZEN 规则集版本、同号 FROZEN 决策点版本、内置合同的全部输入字段与唯一绑定，满足创建资格。</li>
 * <li>规则默认为 {@link RuleSetBuilder#MINIMAL_RULE}，八项静态检查全部通过；{@link #orderedStringRule()} 改为对字符串字段做 GT 比较，
 * 只使 AST_TYPES 失败（ORDERED_TYPE）。</li>
 * <li>Candidate 状态链按首期合同生成：DRAFT 一条创建事件；VALIDATING 追加开始事件，可选八项全通过；REJECTED 再追加八项（AST_TYPES 失败）与拒绝事件。</li>
 * <li>各内容摘要由生产 Canonicalizer 按与持久化事实一致的投影计算，仅用于准备合法夹具。</li>
 *
 * @author Kenny
 */
public final class CandidateBuilder {
    /** Candidate 创建时间基准，UTC 微秒精度；开始与完成事件分别晚一、二小时。 */
    public static final Instant CREATE_TIME = Instant.parse("2026-04-05T06:07:08.123456Z");

    /** 创建与校验的默认操作者，与用例的 Gateway Actor Header 一致。 */
    public static final long ACTOR_ID = 70001L;

    /** 字符串字段做 GT 比较时 AST_TYPES 的违规编码，路径为该规则的谓词节点。 */
    public static final String ORDERED_TYPE = "ORDERED_TYPE";

    private static final List<StaticCheckCode> CHECK_ORDER = List.of(StaticCheckCode.values());

    /** 组成中规则集版本的状态。 */
    private RuleSetVersionStatus ruleSetStatus = RuleSetVersionStatus.FROZEN;

    private boolean orderedStringRule;

    private boolean withoutCompanion;

    private boolean deletedRoot;

    private boolean newerDraftVersion;

    private DecisionReleaseStatus status;

    private boolean checksCompleted;

    private DecisionEnvironment environment = DecisionEnvironment.TEST;

    private String releaseNo;

    private long createdBy = ACTOR_ID;

    private long validatedBy = ACTOR_ID;

    private Instant createTime = CREATE_TIME;

    private CandidateBuilder(DecisionReleaseStatus status, boolean checksCompleted) {
        this.status = status;
        this.checksCompleted = checksCompleted;
    }

    /** 只有可创建 Candidate 的旧发布组成，没有发布记录。 */
    public static CandidateBuilder composition() {
        return new CandidateBuilder(null, false);
    }

    /** 刚创建的 DRAFT/version=1 Candidate。 */
    public static CandidateBuilder draft() {
        return new CandidateBuilder(DecisionReleaseStatus.DRAFT, false);
    }

    /** 已开始校验但尚未写入检查结果的 VALIDATING/version=2 Candidate，即中断恢复起点。 */
    public static CandidateBuilder validationStarted() {
        return new CandidateBuilder(DecisionReleaseStatus.VALIDATING, false);
    }

    /** 八项检查全部通过、保持 VALIDATING/version=2 的 Candidate。 */
    public static CandidateBuilder validationPassed() {
        return new CandidateBuilder(DecisionReleaseStatus.VALIDATING, true);
    }

    /** 八项检查中 AST_TYPES 失败、进入 REJECTED/version=3 的 Candidate。 */
    public static CandidateBuilder rejected() {
        return new CandidateBuilder(DecisionReleaseStatus.REJECTED, true).orderedStringRule();
    }

    /** 规则改为 country_code GT "AE"，加载合法但静态检查 AST_TYPES 失败。 */
    public CandidateBuilder orderedStringRule() {
        this.orderedStringRule = true;
        return this;
    }

    /** 组成中的规则集版本状态；DRAFT 时决策点版本同为 DRAFT。 */
    public CandidateBuilder ruleSetStatus(RuleSetVersionStatus ruleSetStatus) {
        this.ruleSetStatus = ruleSetStatus;
        return this;
    }

    /** 规则集版本没有旧发布组成（决策点版本、输入字段与绑定）。 */
    public CandidateBuilder withoutCompanion() {
        this.withoutCompanion = true;
        return this;
    }

    /** 规则集根已整体删除。 */
    public CandidateBuilder deletedRoot() {
        this.deletedRoot = true;
        return this;
    }

    /** 规则集另有一个更新的 DRAFT 版本作为当前版本，目标版本变为历史版本。 */
    public CandidateBuilder newerDraftVersion() {
        this.newerDraftVersion = true;
        return this;
    }

    /** 发布所属环境，默认 TEST。 */
    public CandidateBuilder environment(DecisionEnvironment environment) {
        this.environment = environment;
        return this;
    }

    /** 覆盖发布根状态，用于构造首期合同之外的根。 */
    public CandidateBuilder status(DecisionReleaseStatus status) {
        this.status = status;
        return this;
    }

    /** 指定发布编号原文；省略时由 Mocker 分配规范正整数。 */
    public CandidateBuilder releaseNo(String releaseNo) {
        this.releaseNo = releaseNo;
        return this;
    }

    public CandidateBuilder createdBy(long createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    /** 开始校验（及完成）事件的操作者。 */
    public CandidateBuilder validatedBy(long validatedBy) {
        this.validatedBy = validatedBy;
        return this;
    }

    public CandidateBuilder createTime(Instant createTime) {
        this.createTime = createTime;
        return this;
    }

    public RuleSetVersionStatus ruleSetStatus() {
        return ruleSetStatus;
    }

    public boolean isOrderedStringRule() {
        return orderedStringRule;
    }

    public boolean isWithoutCompanion() {
        return withoutCompanion;
    }

    public boolean isDeletedRoot() {
        return deletedRoot;
    }

    public boolean hasNewerDraftVersion() {
        return newerDraftVersion;
    }

    /** 发布根状态；只准备组成时为 null。 */
    public DecisionReleaseStatus status() {
        return status;
    }

    public DecisionEnvironment environment() {
        return environment;
    }

    public String releaseNo() {
        return releaseNo;
    }

    public long createdBy() {
        return createdBy;
    }

    public long validatedBy() {
        return validatedBy;
    }

    public Instant createTime() {
        return createTime;
    }

    /** 根版本：DRAFT 1、VALIDATING 2、REJECTED 3，其他状态按 1 处理。 */
    public int version() {
        return switch (status) {
            case VALIDATING -> 2;
            case REJECTED -> 3;
            default -> 1;
        };
    }

    /** 首期状态链的事件数量。 */
    public int stateChangeCount() {
        return switch (status) {
            case VALIDATING -> 2;
            case REJECTED -> 3;
            default -> 1;
        };
    }

    public boolean checksCompleted() {
        return checksCompleted;
    }

    public Instant startTime() {
        return createTime.plusSeconds(3600);
    }

    public Instant completeTime() {
        return createTime.plusSeconds(7200);
    }

    /** 八项检查的状态：只有失败规则的 AST_TYPES 为 FAILED。 */
    public List<StaticCheckSpec> checks(long ruleId) {
        List<StaticCheckSpec> checks = new ArrayList<>();
        for (StaticCheckCode code : CHECK_ORDER) {
            boolean failed = orderedStringRule && code == StaticCheckCode.AST_TYPES;
            checks.add(failed ? failedCheck(code, ORDERED_TYPE, "nodes/" + (ruleId + 1)) : passedCheck(code));
        }
        return List.copyOf(checks);
    }

    /** 一项静态检查的期望事实。 */
    public record StaticCheckSpec(StaticCheckCode checkCode, StaticCheckStatus checkStatus,
        StaticCheckSeverity severity, String errorCode, String errorSummary) {
    }

    public static StaticCheckSpec passedCheck(StaticCheckCode code) {
        return new StaticCheckSpec(code, StaticCheckStatus.PASSED, StaticCheckSeverity.INFO, null, null);
    }

    /** 失败检查的脱敏摘要：首个违规编码与路径及违规数量。 */
    public static StaticCheckSpec failedCheck(StaticCheckCode code, String errorCode, String path) {
        return new StaticCheckSpec(code, StaticCheckStatus.FAILED, StaticCheckSeverity.ERROR, errorCode,
            "first=" + errorCode + "@" + path + "; violation_count=1");
    }

    /** 本 Builder 的规则内容投影。 */
    public RuleProjection ruleProjection() {
        if (!orderedStringRule) {
            return MINIMAL_RULE;
        }
        return new RuleProjection(MINIMAL_RULE.ruleCode(), MINIMAL_RULE.ruleName(), MINIMAL_RULE.priorityNo(), true,
            null, new PredicateProjection(ComparisonOperator.GT, List.of(
                new OperandProjection(OperandRole.LEFT, 1, OperandSource.CONTEXT, "country_code",
                    DecisionValueType.STRING, NullValue.INSTANCE),
                new OperandProjection(OperandRole.RIGHT, 1, OperandSource.LITERAL, null, DecisionValueType.STRING,
                    new StringValue("AE")))),
            MINIMAL_RULE.action());
    }

    /** 内置 CHECKOUT_PREPARE 旧合同（ORDER_PREPARATION v4）的全部规则输入字段；旧候选发布只按旧合同组成。 */
    public static List<DecisionPointAuthoringDescriptor.InputFieldDescriptor> inputFields() {
        return DecisionPointAuthoringDescriptor.legacyCheckoutPrepare().fields();
    }

    /**
     * 已插入的旧发布组成事实；规则 ID 为 ruleId，节点、左操作数、动作依次加一，失败规则的右操作数为 ruleId + 4。
     */
    public record CompositionFacts(long ruleSetId, String ruleSetCode, String ruleSetName, long ruleSetVersionId,
        int versionNo, RuleSetVersionStatus ruleSetStatus, String ruleSetHash, long pointVersionId,
        int pointVersionNo, String pointHash, long bindingId, long ruleId, Map<String, Long> inputFieldIds,
        RuleProjection rule) {
    }

    /** 规则集版本内容摘要。 */
    public static String ruleSetHash(String ruleSetCode, String ruleSetName, int versionNo, RuleProjection rule) {
        return new RiskDecisionCanonicalizerImpl()
            .ruleSetVersion(new RuleSetVersionProjection(POINT_CODE, ruleSetCode, ruleSetName, versionNo, List.of(rule)))
            .sha256LowerHex();
    }

    /** 唯一绑定该规则集版本的决策点版本内容摘要。 */
    public static String pointHash(int pointVersionNo, String ruleSetCode, int versionNo, String ruleSetHash) {
        var point = DecisionPointAuthoringDescriptor.legacyCheckoutPrepare().point();
        return new RiskDecisionCanonicalizerImpl().decisionPointVersion(new DecisionPointVersionProjection(POINT_CODE,
            DecisionBusinessDomain.CHECKOUT, pointVersionNo, point.inputContractCode(), point.inputContractVersion(),
            point.outputContractCode(), point.defaultDecision(), point.evaluationStrategy(), point.failureStrategy(),
            point.timeoutMillis(),
            inputFields().stream().map(field -> new InputFieldProjection(field.fieldCode(), field.valueType(),
                field.requiredFlag(), field.dataClassification(), field.ruleReferenceFlag(),
                field.actionParameterFlag())).toList(),
            List.of(new RuleSetBindingProjection(1, ruleSetCode, versionNo, ruleSetHash)), List.of(), List.of(),
            List.of())).sha256LowerHex();
    }

    /** 发布完整冻结组成的内容摘要。 */
    public static String releaseHash(CompositionFacts facts, long releaseId, int releaseNo) {
        var point = DecisionPointAuthoringDescriptor.legacyCheckoutPrepare().point();
        String pointId = Long.toString(DECISION_POINT_ID);
        String pointVersionId = id(facts.pointVersionId());
        String ruleId = id(facts.ruleId());
        String nodeId = id(facts.ruleId() + 1);
        String countryFieldId = id(facts.inputFieldIds().get("country_code"));
        PredicateProjection predicate = (PredicateProjection) facts.rule().condition();
        List<ReleaseOperandProjection> operands = new ArrayList<>();
        operands.add(new ReleaseOperandProjection(id(facts.ruleId() + 2), nodeId, OperandRole.LEFT, 1,
            OperandSource.CONTEXT, null, countryFieldId, DecisionValueType.STRING, null, null, null, null, null));
        if (predicate.operands().size() == 2) {
            operands.add(new ReleaseOperandProjection(id(facts.ruleId() + 4), nodeId, OperandRole.RIGHT, 1,
                OperandSource.LITERAL, null, null, DecisionValueType.STRING, "AE", null, null, null, null));
        }
        ActionProjection action = facts.rule().action();
        return new RiskDecisionCanonicalizerImpl().decisionRelease(new DecisionReleaseProjection(id(releaseId),
            releaseNo, pointId, pointVersionId, DecisionEnvironment.TEST,
            new ReleaseDecisionPointProjection(pointId, POINT_CODE, DecisionBusinessDomain.CHECKOUT),
            new ReleasePointVersionProjection(pointId, POINT_CODE, pointVersionId, facts.pointVersionNo(),
                point.inputContractCode(), point.inputContractVersion(), point.outputContractCode(),
                point.defaultDecision(), point.evaluationStrategy(), point.failureStrategy(), point.timeoutMillis(),
                facts.pointHash()),
            inputFields().stream().map(field -> new ReleaseInputFieldProjection(
                id(facts.inputFieldIds().get(field.fieldCode())), pointVersionId, field.fieldCode(), field.valueType(),
                field.requiredFlag(), field.dataClassification(), field.ruleReferenceFlag(),
                field.actionParameterFlag())).toList(),
            List.of(new ReleaseRuleSetBindingProjection(id(facts.bindingId()), pointVersionId,
                id(facts.ruleSetVersionId()), 1)),
            List.of(new ReleaseRuleSetVersionProjection(id(facts.ruleSetVersionId()), pointId, facts.ruleSetCode(),
                facts.versionNo(), facts.ruleSetHash())),
            List.of(new ReleaseRuleProjection(ruleId, id(facts.ruleSetVersionId()), facts.rule().ruleCode(),
                facts.rule().priorityNo(), true, null)),
            List.of(new ReleaseNodeProjection(nodeId, ruleId, null, NodeType.PREDICATE, null,
                predicate.comparisonOperator(), 1)),
            operands,
            List.of(new ReleaseActionVersionProjection(id(ALLOW_ACTION_VERSION_ID), action.actionCode(),
                action.actionCategory(), action.actionVersionNo(), action.handlerCode(), action.implementationVersion(),
                action.implementationHash(), action.terminalFlag(), action.decisionCode(), action.mergePolicy(),
                action.shadowSupportedFlag())),
            List.of(new ReleaseRuleActionProjection(id(facts.ruleId() + 3), ruleId, id(ALLOW_ACTION_VERSION_ID), 1)),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of())).sha256LowerHex();
    }

    private static String id(long value) {
        return Long.toString(value);
    }
}
