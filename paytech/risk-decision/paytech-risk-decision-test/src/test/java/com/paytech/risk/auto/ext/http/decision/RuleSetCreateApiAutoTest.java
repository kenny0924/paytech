package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.BUSINESS_CHECK_FAILED;
import static com.paytech.foundation.common.error.CommonErrorCode.CONCURRENT_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode.RISK_DICTIONARY_SELECTION_UNAVAILABLE;
import static com.paytech.risk.decision.api.enums.RiskDecisionConfigurationErrorCode.RISK_RULE_SET_DELETED;
import static com.paytech.risk.decision.api.enums.RiskVariableErrorCode.*;
import static com.paytech.risk.decision.domain.enums.RuleSetVersionStatus.*;
import static com.paytech.risk.test.builder.decision.RuleSetBuilder.versions;
import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.decision.api.DecisionRuleSetManagementApi;
import com.paytech.risk.decision.api.DecisionRuleSetManagementModels.AdditionalAction;
import com.paytech.risk.decision.api.DecisionRuleSetManagementModels.OperandRequestBody;
import com.paytech.risk.decision.api.DecisionRuleSetManagementModels.RuleRequestBody;
import com.paytech.risk.decision.api.request.CreateRuleSetVersionRequestBody;
import com.paytech.risk.decision.api.enums.RiskVariableErrorCode;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.DictionaryStatus;
import com.paytech.risk.decision.domain.enums.GroupOperator;
import com.paytech.risk.decision.domain.enums.NodeType;
import com.paytech.risk.decision.domain.enums.OperandRole;
import com.paytech.risk.decision.domain.enums.OperandSource;
import com.paytech.risk.test.base.RiskDecisionMockTestBase;
import com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder;
import com.paytech.risk.test.checker.decision.RuleSetCommandResponseChecker;
import com.paytech.risk.test.checker.decision.RuleSetStoreChecker;
import com.paytech.risk.test.checker.decision.RuleSetStoreChecker.ExpectedAction;
import com.paytech.risk.test.checker.decision.RuleSetStoreChecker.ExpectedNode;
import com.paytech.risk.test.checker.decision.RuleSetStoreChecker.ExpectedOperand;
import com.paytech.risk.test.checker.decision.RuleSetStoreChecker.ExpectedRule;
import com.paytech.risk.test.checker.decision.RuleSetStoreChecker.Snapshot;
import com.paytech.risk.test.mocker.decision.RuleSetMocker;
import com.paytech.risk.test.mocker.decision.RuleSetMocker.MockedRuleSet;

/**
 * 保存规则集新 DRAFT 版本的业务结果
 * <li>正常流程：首个版本创建管理根；基于当前草稿保存时退役原草稿，基于冻结版本保存时保留冻结；完整规则闭包按优先级与先序持久化。</li>
 * <li>变量引用：变量不存在、不适用于条件位置、类型或量纲不符、超出可选集合时按变量错误码拒绝。</li>
 * <li>附加动作：结账已接入运行期，冷却与打标签等附加动作不再开放，保存时拒绝（400）。</li>
 * <li>字典引用：拒绝原因未登记、已停用时拒绝（409）。</li>
 * <li>版本冲突：已存在编码未带基准、基准不是当前版本或内容摘要不符、基准不存在、资源锁被占用时返回并发冲突（409）；已删除编码不可复活（409）。</li>
 * <li>失败关闭：动作目录漂移、管理根当前指针损坏时返回 500。所有拒绝都不改写任何持久化事实。</li>
 *
 * @see DecisionRuleSetManagementApi#createRuleSetVersion(String, String, CreateRuleSetVersionRequestBody)
 */
class RuleSetCreateApiAutoTest extends RiskDecisionMockTestBase {
    private static final String PATH = "/decision-rule-sets/v1/rule-sets";

    private static final long ACTOR = 70001L;

    private static final long ALLOW_ID = 240001L;

    private static final long REVIEW_ID = 240002L;

    private static final long DENY_ID = 240003L;

    @Autowired
    private RuleSetMocker ruleSets;

    @Autowired
    private RuleSetStoreChecker store;

    /** 首个版本创建管理根与 DRAFT v1，规则闭包与请求一致。 */
    @Test
    void test_normalFlow() {
        String code = ruleSets.newRuleSetCode();

        RuleSetCommandResponseChecker response = invoke(template(code).build()).assertCreated(code, 1, ACTOR);

        Snapshot after = store.snapshot(code);
        long versionId = response.ruleSetVersionId();
        Instant time = response.commandTime();
        store.assertCreatedRoot(after, versionId, time);
        store.assertDraftVersion(after, 1, versionId, "Checkout country rules", "Initial country rules",
            response.contentHash(), ACTOR, time);
        store.assertClosure(versionId, time, countryAllowRule());
    }

    /** 基于当前草稿保存：原草稿退役，管理根推进到新版本，更早的冻结版本不变。 */
    @Test
    void test_normalFlow_nextVersionRetiresDraftBase() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT));
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        RuleSetCommandResponseChecker response = invoke(template(ruleSet.ruleSetCode()).ruleSetName("Next rules")
            .changeReason("Tighten country rules").base(id(ruleSet, 2), ruleSet.version(2).contentHash()).build())
            .assertCreated(ruleSet.ruleSetCode(), 3, ACTOR);

        Snapshot after = store.snapshot(ruleSet.ruleSetCode());
        Instant time = response.commandTime();
        store.assertVersionChanged(before, after, 1, Map.of());
        store.assertVersionChanged(before, after, 2,
            Map.of("status", RETIRED.getCode(), "update_time", time));
        store.assertDraftVersion(after, 3, response.ruleSetVersionId(), "Next rules", "Tighten country rules",
            response.contentHash(), ACTOR, time);
        store.assertAdvancedRoot(before, after, response.ruleSetVersionId(), time);
        store.assertClosure(response.ruleSetVersionId(), time, countryAllowRule());
    }

    /** 基于当前冻结版本保存：冻结版本保持不变，新草稿成为当前版本。 */
    @Test
    void test_normalFlow_nextVersionKeepsFrozenBase() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN));
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        RuleSetCommandResponseChecker response = invoke(template(ruleSet.ruleSetCode())
            .base(id(ruleSet, 1), ruleSet.version(1).contentHash()).build())
            .assertCreated(ruleSet.ruleSetCode(), 2, ACTOR);

        Snapshot after = store.snapshot(ruleSet.ruleSetCode());
        store.assertVersionChanged(before, after, 1, Map.of());
        store.assertAdvancedRoot(before, after, response.ruleSetVersionId(), response.commandTime());
    }

    /** 分组条件、集合比较、拒绝原因与冷却附加动作；规则按优先级存储，名称与原因去除首尾空白。 */
    @Test
    void test_normalFlow_completeConfiguration() {
        String code = ruleSets.newRuleSetCode();
        String reasonCode = ruleSets.mockRejectionReason(DictionaryStatus.ACTIVE);
        var deny = rule("COUNTRY_DENY", "  Country deny  ", 20, reasonCode, "Blocked country", action(DENY),
            List.of(), group("all", null, 1, "AND"),
            predicate("country", "all", 1, "IN", variable("LEFT", 1, "INPUT_COUNTRY_CODE", "STRING"),
                literal("RIGHT", 1, "STRING", "AE"), literal("RIGHT", 2, "STRING", "SA")),
            predicate("entity", "all", 2, "IS_NOT_NULL", variable("LEFT", 1, "INPUT_ENTITY_NO", "STRING")));
        var review = rule("MCC_REVIEW", "MCC review", 5, null, null, action(REVIEW), List.of(),
            predicate("mcc", null, 1, "EQ", variable("LEFT", 1, "INPUT_MCC_CODE", "STRING"),
                literal("RIGHT", 1, "STRING", "5411")));

        RuleSetCommandResponseChecker response = invoke(template(code).ruleSetName("  Combined rules ")
            .changeReason("  Combined change ").rules(deny, review).build()).assertCreated(code, 1, ACTOR);

        Snapshot after = store.snapshot(code);
        Instant time = response.commandTime();
        store.assertDraftVersion(after, 1, response.ruleSetVersionId(), "Combined rules", "Combined change",
            response.contentHash(), ACTOR, time);
        store.assertClosure(response.ruleSetVersionId(), time,
            new ExpectedRule("MCC_REVIEW", "MCC review", 5, true, null, null,
                List.of(predicateNode(-1, 1, ComparisonOperator.EQ, variableOperand("INPUT_MCC_CODE"),
                    stringOperand(1, "5411"))),
                List.of(new ExpectedAction(REVIEW_ID, 1, null, List.of()))),
            new ExpectedRule("COUNTRY_DENY", "Country deny", 20, true, reasonCode, "Blocked country",
                List.of(new ExpectedNode(-1, NodeType.GROUP.getCode(), GroupOperator.AND.getCode(), null, 1, List.of()),
                    predicateNode(0, 1, ComparisonOperator.IN, variableOperand("INPUT_COUNTRY_CODE"),
                        stringOperand(1, "AE"), stringOperand(2, "SA")),
                    predicateNode(0, 2, ComparisonOperator.IS_NOT_NULL, variableOperand("INPUT_ENTITY_NO"))),
                List.of(new ExpectedAction(DENY_ID, 1, null, List.of()))));
    }

    /** 统一变量引用不合格时按变量错误码拒绝，不写入任何事实。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("variableViolations")
    void test_businessRuleViolation_variableReference(String scenario,
        Function<String, RuleSetCreateRequestBuilder> request, int status, RiskVariableErrorCode code) {
        String ruleSetCode = ruleSets.newRuleSetCode();

        invoke(request.apply(ruleSetCode).build()).assertProblem(status, code.name());

        store.assertNothingWritten(ruleSetCode);
    }

    /** 拒绝原因必须是已登记且启用的字典项。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("dictionaryViolations")
    void test_businessRuleViolation_dictionarySelection(String scenario, DictionaryStatus reasonStatus) {
        String code = ruleSets.newRuleSetCode();
        String reasonCode = reasonStatus == null ? "MOCK_UNREGISTERED_REASON"
            : ruleSets.mockRejectionReason(reasonStatus);
        var deny = rule("COUNTRY_DENY", "Country deny", 10, reasonCode, "Blocked country", action(DENY), List.of(),
            predicate("root", null, 1, "EQ", variable("LEFT", 1, "INPUT_COUNTRY_CODE", "STRING"),
                literal("RIGHT", 1, "STRING", "AE")));

        invoke(template(code).rules(deny).build()).assertProblem(409, RISK_DICTIONARY_SELECTION_UNAVAILABLE.getCode());

        store.assertNothingWritten(code);
    }

    /** 结账运行期不开放附加动作：带冷却或打标签的规则保存时拒绝，不写入任何事实。 */
    @Test
    void test_businessRuleViolation_additionalActionNotSupported() {
        String code = ruleSets.newRuleSetCode();

        invoke(template(code).rules(withAction(cooldownOf(CLIENT_VARIABLE))).build()).assertProblem(400,
            BUSINESS_CHECK_FAILED.getCode());
        invoke(template(code).rules(withAction(tag(1, "MOCK_TAG"))).build()).assertProblem(400,
            BUSINESS_CHECK_FAILED.getCode());

        store.assertNothingWritten(code);
    }

    /** 拒绝规则缺少原因代码或文案。 */
    @Test
    void test_businessRuleViolation_denyWithoutReason() {
        String code = ruleSets.newRuleSetCode();
        String reasonCode = ruleSets.mockRejectionReason(DictionaryStatus.ACTIVE);
        var predicate = predicate("root", null, 1, "EQ", variable("LEFT", 1, "INPUT_COUNTRY_CODE", "STRING"),
            literal("RIGHT", 1, "STRING", "AE"));

        invoke(template(code).rules(rule("DENY_RULE", "Deny", 10, reasonCode, null, action(DENY), List.of(),
            predicate)).build()).assertProblem(400, BUSINESS_CHECK_FAILED.getCode());
        invoke(template(code).rules(rule("DENY_RULE", "Deny", 10, null, null, action(DENY), List.of(),
            predicate)).build()).assertProblem(400, BUSINESS_CHECK_FAILED.getCode());

        store.assertNothingWritten(code);
    }

    /** 基准版本与当前版本不一致时返回并发冲突，不追加版本。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("staleBases")
    void test_businessRuleViolation_staleBase(String scenario,
        Function<MockedRuleSet, RuleSetCreateRequestBuilder> request) {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT));
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        invoke(request.apply(ruleSet).build()).assertProblem(409, CONCURRENT_REQUEST.getCode());

        store.assertUnchanged(before);
    }

    /** 编码尚不存在却携带基准版本。 */
    @Test
    void test_businessRuleViolation_baseForUnknownRuleSet() {
        String code = ruleSets.newRuleSetCode();

        invoke(template(code).base("123456789012345", "a".repeat(64)).build()).assertProblem(409,
            CONCURRENT_REQUEST.getCode());

        store.assertNothingWritten(code);
    }

    /** 已整体删除的编码不可再保存新版本。 */
    @Test
    void test_businessRuleViolation_deletedRuleSet() {
        MockedRuleSet deleted = ruleSets.mock(versions(FROZEN, DRAFT).deleted());
        Snapshot before = store.snapshot(deleted.ruleSetCode());

        invoke(template(deleted.ruleSetCode()).base(id(deleted, 2), deleted.version(2).contentHash()).build())
            .assertProblem(409, RISK_RULE_SET_DELETED.getCode());

        store.assertUnchanged(before);
    }

    /** 同一规则集资源锁被其他命令占用时快速失败。 */
    @Test
    void test_businessRuleViolation_resourceLocked() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(DRAFT));
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());
        ruleSets.holdResourceLock(ruleSet.ruleSetCode());

        invoke(template(ruleSet.ruleSetCode()).base(id(ruleSet, 1), ruleSet.version(1).contentHash()).build())
            .assertProblem(409, CONCURRENT_REQUEST.getCode());

        store.assertUnchanged(before);
    }

    /** 持久化动作目录与工程注册表漂移时失败关闭。 */
    @Test
    void test_businessRuleViolation_actionCatalogDriftFailsClosed() {
        String code = ruleSets.newRuleSetCode();
        ruleSets.driftActionCatalog();

        invoke(template(code).build()).assertProblem(500, INTERNAL_ERROR.getCode());

        store.assertNothingWritten(code);
    }

    /** 管理根当前指针不是最新版本时失败关闭。 */
    @Test
    void test_businessRuleViolation_staleHeadFailsClosed() {
        MockedRuleSet ruleSet = ruleSets.mock(versions(FROZEN, DRAFT));
        ruleSets.corruptCurrentVersion(ruleSet, 1);
        Snapshot before = store.snapshot(ruleSet.ruleSetCode());

        invoke(template(ruleSet.ruleSetCode()).base(id(ruleSet, 2), ruleSet.version(2).contentHash()).build())
            .assertProblem(500, INTERNAL_ERROR.getCode());

        store.assertUnchanged(before);
    }

    static Stream<Arguments> variableViolations() {
        return Stream.of(
            violation("条件变量不存在", code -> template(code).rules(condition("EQ",
                variable("LEFT", 1, "MOCK_UNKNOWN_VARIABLE", "STRING"), literal("RIGHT", 1, "STRING", "AE"))), 404,
                VARIABLE_NOT_FOUND),
            violation("变量不允许用于规则条件", code -> template(code).rules(condition("EQ",
                variable("LEFT", 1, "NODE_DECISION", "STRING"), literal("RIGHT", 1, "STRING", DecisionValue.ALLOW.getCode()))),
                409,
                VARIABLE_SCOPE_MISMATCH),
            violation("声明类型与变量库不一致", code -> template(code).rules(condition("EQ",
                variable("LEFT", 1, "INPUT_COUNTRY_CODE", "NUMBER"), literal("RIGHT", 1, "NUMBER", "1"))), 409,
                VARIABLE_TYPE_MISMATCH),
            violation("金额变量与无量纲字面量比较", code -> template(code).rules(condition("GT",
                variable("LEFT", 1, "INPUT_NET_AMOUNT", "NUMBER"), literal("RIGHT", 1, "NUMBER", "100"))), 409,
                VARIABLE_UNIT_MISMATCH),
            violation("比较值不属于变量可选集合", code -> template(code).rules(condition("EQ",
                variable("LEFT", 1, "INPUT_ENTITY_SOURCE_TYPE", "STRING"), literal("RIGHT", 1, "STRING", "OTHER"))),
                409, VARIABLE_DOMAIN_MISMATCH));
    }

    static Stream<Arguments> dictionaryViolations() {
        return Stream.of(Arguments.of("拒绝原因未登记", null), Arguments.of("拒绝原因已停用", DictionaryStatus.DISABLED));
    }

    static Stream<Arguments> staleBases() {
        return Stream.of(
            Arguments.of("已有规则集未带基准",
                stale(ruleSet -> template(ruleSet.ruleSetCode()))),
            Arguments.of("基准为历史版本",
                stale(ruleSet -> template(ruleSet.ruleSetCode()).base(id(ruleSet, 1),
                    ruleSet.version(1).contentHash()))),
            Arguments.of("基准内容摘要不符",
                stale(ruleSet -> template(ruleSet.ruleSetCode()).base(id(ruleSet, 2), "f".repeat(64)))),
            Arguments.of("基准版本不存在",
                stale(ruleSet -> template(ruleSet.ruleSetCode()).base("123456789012345",
                    ruleSet.version(2).contentHash()))));
    }

    private static Arguments violation(String scenario, Function<String, RuleSetCreateRequestBuilder> request,
        int status, RiskVariableErrorCode code) {
        return Arguments.of(scenario, request, status, code);
    }

    private static Function<MockedRuleSet, RuleSetCreateRequestBuilder>
        stale(Function<MockedRuleSet, RuleSetCreateRequestBuilder> request) {
        return request;
    }

    /** 单条 ALLOW 规则，根节点为给定比较。 */
    private static RuleRequestBody condition(
        String operator, OperandRequestBody... operands) {
        return rule("CHECK_RULE", "Check rule", 10, action(ALLOW), predicate("root", null, 1, operator, operands));
    }

    /** 以指定变量为主体的冷却附加动作。 */
    private static AdditionalAction cooldownOf(String subjectVariableCode) {
        return new AdditionalAction(1, COOLDOWN, 1, CLIENT_TARGET, cooldown(1, "60000").getParameters(),
            subjectVariableCode);
    }

    /** 模板条件加一个附加动作。 */
    private static RuleRequestBody withAction(
        AdditionalAction additional) {
        return rule("ACTION_RULE", "Action rule", 10, null, null, action(ALLOW), List.of(additional),
            predicate("root", null, 1, "EQ", variable("LEFT", 1, "INPUT_COUNTRY_CODE", "STRING"),
                literal("RIGHT", 1, "STRING", "AE")));
    }

    /** 模板规则的期望持久化闭包。 */
    private static ExpectedRule countryAllowRule() {
        return new ExpectedRule("COUNTRY_ALLOW", "Country allow", 10, true, null, null,
            List.of(predicateNode(-1, 1, ComparisonOperator.EQ, variableOperand("INPUT_COUNTRY_CODE"),
                stringOperand(1, "AE"))),
            List.of(new ExpectedAction(ALLOW_ID, 1, null, List.of())));
    }

    private static ExpectedNode predicateNode(int parent, int sequenceNo, ComparisonOperator operator,
        ExpectedOperand... operands) {
        return new ExpectedNode(parent, NodeType.PREDICATE.getCode(), null, operator.getCode(), sequenceNo,
            List.of(operands));
    }

    private static ExpectedOperand variableOperand(String variableCode) {
        return new ExpectedOperand(OperandRole.LEFT.getCode(), 1, OperandSource.VARIABLE.getCode(),
            DecisionValueType.STRING.getCode(), variableCode, null, null, null);
    }

    private static ExpectedOperand stringOperand(int valueSequence, String value) {
        return new ExpectedOperand(OperandRole.RIGHT.getCode(), valueSequence, OperandSource.LITERAL.getCode(),
            DecisionValueType.STRING.getCode(), null, value, null, null);
    }

    private static String id(MockedRuleSet ruleSet, int versionNo) {
        return Long.toString(ruleSet.version(versionNo).ruleSetVersionId());
    }

    private RuleSetCommandResponseChecker invoke(CreateRuleSetVersionRequestBody body) {
        String requestId = "risk-rule-set-create-" + UUID.randomUUID();
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Gateway-Actor-Subject", Long.toString(ACTOR));
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        headers.setContentType(MediaType.APPLICATION_JSON);
        Instant before = Instant.now();
        var result = http.post(PATH, headers, objectMapper.writeValueAsString(body));
        return new RuleSetCommandResponseChecker(result, objectMapper, requestId, before, Instant.now());
    }
}
