package com.paytech.risk.auto.ext.http.evaluation;

import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.api.DecisionRuleSetManagementModels.ConditionNodeRequestBody;
import com.paytech.risk.decision.api.DecisionRuleSetManagementModels.OperandRequestBody;
import com.paytech.risk.decision.api.DecisionRuleSetManagementModels.RuleRequestBody;
import com.paytech.risk.decision.api.evaluation.RiskDecisionApi;
import com.paytech.risk.decision.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.decision.domain.enums.AuthenticationRequirement;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.FlowEdgeType;
import com.paytech.risk.decision.domain.enums.FlowNodeType;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;
import com.paytech.risk.decision.domain.enums.VelocityMetric;
import com.paytech.risk.test.base.AbstractPaymentRiskApiTest;
import com.paytech.risk.test.builder.control.TierQuotaVariableBuilder;
import com.paytech.risk.test.builder.decision.FlowBuilder.EdgeSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.GraphSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.NodeSpec;
import com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder;
import com.paytech.risk.test.builder.evaluation.PaymentRiskRequestBuilder;
import com.paytech.risk.test.checker.evaluation.EvaluationResponseChecker.Assessed;
import com.paytech.risk.test.mapper.control.LimitTierTestMapper;
import com.paytech.risk.test.mocker.control.LimitTierMocker;

/**
 * 按档位分支的支付尝试评估（AC-06 中引擎已支持的部分）：经真实配置入口建档位名单变量、额度常量、币种集合、规则集与带档位分支的流程，
 * 激活后经支付评估入口执行
 * <li>START 只能有一条无条件出口，流程先进入档位分支节点，再按四张 TIER 名单分支：RESTRICTED 拒绝；VERIFICATION / OBSERVATION / STANDARD 进入各自额度规则集；不在任何档位走拒绝。</li>
 * <li>额度规则：币种不在本档位集合 → 拒绝（STANDARD 无集合，未配置币种放行）；“支付币种 = {CCY} 且金额 &gt; 单笔常量”→ 拒绝；
 * “支付币种 = {CCY} 且当日 / 近 30 日放行合计（含本次）&gt; 日 / 月常量”→ 拒绝。常量为支付点 MONEY 常量、币种来源为支付币种。</li>
 * <li>日 / 月额度读按 UTC 自然日汇总的放行金额（DAILY_SUM_AMOUNT）：当日、近 30 个 UTC 日含当日；被拒与重放不进汇总；
 * USD 与 AED 分别累计；并发放行在同一汇总行上不丢失更新。</li>
 * <li>常量在评估时读当前值：改常量后下一次评估即用新值，无需新发布。</li>
 *
 * @see RiskDecisionApi#evaluate(EvaluateDecisionRequestBody)
 */
class PaymentRiskLimitTierApiAutoTest extends AbstractPaymentRiskApiTest {
    /** VERIFICATION 的 USD 单笔上限常量。 */
    private static final String VERIFICATION_USD_SINGLE = "TIER_VERIFICATION_USD_SINGLE";

    @Autowired
    private LimitTierMocker tierMocker;

    @Autowired
    private LimitTierTestMapper tierStore;

    /** 每例建档位名单变量、放行累计变量、额度常量与集合、三个档位规则集与带档位分支的流程，并激活。 */
    @BeforeEach
    void setUpClassMocks() {
        String inVerification = tierVariable(LimitTier.VERIFICATION);
        String inObservation = tierVariable(LimitTier.OBSERVATION);
        String inStandard = tierVariable(LimitTier.STANDARD);
        String inRestricted = tierVariable(LimitTier.RESTRICTED);
        String day = dailyVariable("T_PAY_CLIENT_ALLOWED_DAY", 86_400);
        String month = dailyVariable("T_PAY_CLIENT_ALLOWED_30D", 2_592_000);
        quotaConstant(VERIFICATION_USD_SINGLE, "50");
        quotaConstant("TIER_VERIFICATION_USD_DAY", "250");
        quotaConstant("TIER_VERIFICATION_USD_MONTH", "400");
        quotaConstant("TIER_VERIFICATION_AED_DAY", "1000");
        quotaConstant("TIER_VERIFICATION_AED_MONTH", "3000");
        quotaConstant("TIER_VERIFICATION_AED_SINGLE", "200");
        quotaConstant("TIER_OBSERVATION_USD_SINGLE", "2000");
        quotaConstant("TIER_STANDARD_USD_SINGLE", "10000");
        currencySet("TIER_VERIFICATION_CURRENCIES", "USD", "AED");
        currencySet("TIER_OBSERVATION_CURRENCIES", "USD");
        String reason = rejectionReason();
        long verification = ruleSet("T_TIER_V", reason,
            notIn("TIER_VERIFICATION_CURRENCIES"), single("USD", VERIFICATION_USD_SINGLE),
            single("AED", "TIER_VERIFICATION_AED_SINGLE"), accumulated("USD", day, "TIER_VERIFICATION_USD_DAY"),
            accumulated("USD", month, "TIER_VERIFICATION_USD_MONTH"), accumulated("AED", day, "TIER_VERIFICATION_AED_DAY"),
            accumulated("AED", month, "TIER_VERIFICATION_AED_MONTH"));
        long observation = ruleSet("T_TIER_O", reason, notIn("TIER_OBSERVATION_CURRENCIES"),
            single("USD", "TIER_OBSERVATION_USD_SINGLE"));
        long standard = ruleSet("T_TIER_S", reason, single("USD", "TIER_STANDARD_USD_SINGLE"));
        long gate = frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_TIER_GATE")).pointCode(PAYMENT)
            .rules(rule("TIER_GATE", "Tier gate", 10, null, null, null, List.of(), predicate("root", null, 1, "GT",
                variableOperand("PAY_INPUT_AMOUNT", "NUMBER"), money("0")))).build());
        activeRelease(saveFlow(newCode("T_TIER_FLOW"), tierGraph(new long[] {gate, verification, observation, standard},
            new String[] {inRestricted, inVerification, inObservation, inStandard}, reason)));
    }

    /** 不在任何档位：走无档位拒绝，写评估与拦截事件。 */
    @Test
    void test_businessRuleViolation_noTierDenied() {
        long clientId = 81_100_001L;

        Assessed assessed = deny(clientId, "10", "USD");

        assertAll(() -> assertEquals(List.of("start", "gate", "deny_not_set"), nodes(assessed)),
            () -> assertEquals(List.of(VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED.getCode(),
                VelocityEventCode.PAYMENT_ATTEMPT_REJECTED.getCode()),
                store.velocityEventCodes(store.assessmentIdOf(assessed.evaluationId()))));
    }

    /** RESTRICTED：直接走受限拒绝，不进入额度规则集。 */
    @Test
    void test_businessRuleViolation_restrictedDenied() {
        long clientId = 81_100_002L;
        tierMocker.mockRestricted(clientId, LimitTier.STANDARD);

        assertEquals(List.of("start", "gate", "deny_restricted"), nodes(deny(clientId, "10", "USD")));
    }

    /**
     * VERIFICATION：单笔超额拒绝；USD 当日累计到上限后拒绝；被拒的评估不进日汇总；AED 另行累计仍放行；
     * 同一支付尝试重放不重复累加
     */
    @Test
    void test_normalFlow_verificationSingleAndDaily() {
        long clientId = 81_100_003L;
        tierMocker.mockTier(clientId, LimitTier.VERIFICATION);

        assertEquals(List.of("start", "gate", "verification", "deny_limit"), nodes(deny(clientId, "60", "USD")));
        EvaluateDecisionRequestBody first = request(clientId, "50", "USD");
        assess(first).assertResult(DecisionValue.ALLOW.getCode(), AuthenticationRequirement.NOT_REQUIRED.getCode());
        for (int attempt = 0; attempt < 4; attempt++) {
            allow(clientId, "50", "USD");
        }
        deny(clientId, "10", "USD");
        Assessed aed = allow(clientId, "150", "AED");
        assess(first).assertResult(DecisionValue.ALLOW.getCode(), AuthenticationRequirement.NOT_REQUIRED.getCode());

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        assertAll(() -> assertEquals(List.of("start", "gate", "verification", "allow"), nodes(aed)),
            () -> assertEquals(List.of(VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED.getCode(),
                VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED.getCode()),
                store.velocityEventCodes(store.assessmentIdOf(aed.evaluationId()))),
            () -> assertEquals(List.of(daily("AED", today, "150", 1), daily("USD", today, "250", 5)),
                dailyRows(clientId), "被拒与重放不进日汇总，币种分行"));
    }

    /**
     * 30 日额度按 UTC 自然日：昨日 300 计入 30 日（上限 400）但不计入当日（上限 250，否则首笔即超）；
     * 30 天前的 1000 不计入（否则首笔即超）；第三笔使 30 日合计 450 超限
     */
    @Test
    void test_edgeCase_monthWindowByUtcDay() {
        long clientId = 81_100_008L;
        tierMocker.mockTier(clientId, LimitTier.VERIFICATION);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        tierStore.insertDailyTotal(8_200_000_001L, clientId, VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED.getCode(), "USD",
            today.minusDays(1), new BigDecimal("300"), 6);
        tierStore.insertDailyTotal(8_200_000_002L, clientId, VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED.getCode(), "USD",
            today.minusDays(30), new BigDecimal("1000"), 9);

        allow(clientId, "50", "USD");
        allow(clientId, "50", "USD");
        deny(clientId, "50", "USD");
    }

    /** 同一 Client、币种、UTC 日的并发放行：日汇总行按行锁累加，金额与笔数不丢失。 */
    @Test
    void test_edgeCase_concurrentDailyTotal() throws Exception {
        long clientId = 81_100_009L;
        tierMocker.mockTier(clientId, LimitTier.STANDARD);
        int concurrency = 8;
        CyclicBarrier ready = new CyclicBarrier(concurrency);
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        List<Future<Assessed>> calls = new ArrayList<>();
        try {
            for (int index = 0; index < concurrency; index++) {
                calls.add(pool.submit(() -> {
                    ready.await(10, TimeUnit.SECONDS);
                    return allow(clientId, "10", "USD");
                }));
            }
            for (Future<Assessed> call : calls) {
                call.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(List.of(daily("USD", LocalDate.now(ZoneOffset.UTC), "80", concurrency)), dailyRows(clientId));
    }

    /** 期望的日汇总行。 */
    private static List<Object> daily(String currency, LocalDate date, String amount, int count) {
        return List.of(VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED.getCode(), currency, date, new BigDecimal(amount).setScale(8),
            count);
    }

    /** Client 的日汇总行（事件、币种、日期、金额、笔数）。 */
    private List<List<Object>> dailyRows(long clientId) {
        return tierStore.dailyTotals(clientId).stream().map(row -> List.<Object>of(row.get("event_code"), row.get("currency"),
            row.get("event_date") instanceof java.sql.Date date ? date.toLocalDate() : row.get("event_date"), row.get("amount"),
            ((Number)row.get("event_count")).intValue())).toList();
    }

    /** 未配置币种：VERIFICATION 拒绝（不在币种集合）；STANDARD 放行（不设额度）。 */
    @Test
    void test_edgeCase_unconfiguredCurrency() {
        long verification = 81_100_004L;
        tierMocker.mockTier(verification, LimitTier.VERIFICATION);
        long standard = 81_100_005L;
        tierMocker.mockTier(standard, LimitTier.STANDARD);

        deny(verification, "10", "SAR");
        assertEquals(List.of("start", "gate", "standard", "allow"), nodes(allow(standard, "10", "SAR")));
        allow(standard, "9000", "USD");
        deny(standard, "10001", "USD");
    }

    /** OBSERVATION：按观察档的单笔额度；集合外币种拒绝。 */
    @Test
    void test_normalFlow_observationQuota() {
        long clientId = 81_100_006L;
        tierMocker.mockObservation(clientId, 3, 30, 100);

        allow(clientId, "1500", "USD");
        deny(clientId, "2500", "USD");
        deny(clientId, "10", "AED");
    }

    /** 改额度常量即时生效：同一发布下一次评估使用新值。 */
    @Test
    void test_edgeCase_constantChangeTakesEffect() {
        long clientId = 81_100_007L;
        tierMocker.mockTier(clientId, LimitTier.VERIFICATION);
        deny(clientId, "60", "USD");

        store.updateConstantNumber(VERIFICATION_USD_SINGLE, new BigDecimal("100"));

        allow(clientId, "60", "USD");
    }

    /** 期望放行的支付尝试评估。 */
    private Assessed allow(long clientId, String amount, String currency) {
        return assess(request(clientId, amount, currency)).assertResult(DecisionValue.ALLOW.getCode(),
            AuthenticationRequirement.NOT_REQUIRED.getCode());
    }

    /** 期望拒绝的支付尝试评估。 */
    private Assessed deny(long clientId, String amount, String currency) {
        return assess(request(clientId, amount, currency)).assertResult(DecisionValue.DENY.getCode(), null);
    }

    /** 指定 Client、金额与币种的新支付尝试。 */
    private static EvaluateDecisionRequestBody request(long clientId, String amount, String currency) {
        return PaymentRiskRequestBuilder.template().input("clientId", Long.toString(clientId)).amount(amount)
            .currency(currency).build();
    }

    /** 评估经过的流程节点。 */
    private List<Object> nodes(Assessed assessed) {
        return store.flowTraces(assessed.evaluationId()).stream().map(row -> row.get("node_key")).toList();
    }

    /** 档位名单命中变量（CLIENT 主体）。 */
    private String tierVariable(LimitTier tier) {
        String code = newCode("T_PAY_IN_" + tier.getCode());
        createListVariable(code, tier.listCode(), RiskSubjectType.CLIENT.getCode());
        return code;
    }

    /** 按 UTC 日汇总的 Client 放行金额累计变量（计入本次）。 */
    private String dailyVariable(String prefix, int windowSeconds) {
        String code = newCode(prefix);
        createVelocityVariable(code, VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED.getCode(), RiskSubjectType.CLIENT.getCode(),
            VelocityMetric.DAILY_SUM_AMOUNT.getCode(), null, windowSeconds, true);
        return code;
    }

    /** 以支付币种为币种来源的额度常量。 */
    private void quotaConstant(String code, String amount) {
        postOk("/decision-variables/v1/variables", TierQuotaVariableBuilder.quota(code, amount));
    }

    /** 全局币种集合及其成员。 */
    private void currencySet(String code, String... currencies) {
        postOk("/decision-variables/v1/variables", TierQuotaVariableBuilder.currencySet(code));
        assertTrue(http.exchange(HttpMethod.PUT, "/decision-variables/v1/variables/" + code + "/members", headers(),
            TierQuotaVariableBuilder.currencies(currencies)).getStatus().is2xxSuccessful(), "替换币种集合成员");
    }

    /** 冻结一个档位额度规则集，每条规则命中即拒绝。 */
    private long ruleSet(String prefix, String reason, ConditionNodeRequestBody[]... rules) {
        RuleRequestBody[] bodies = new RuleRequestBody[rules.length];
        for (int index = 0; index < rules.length; index++) {
            String code = "LIMIT_" + (index + 1);
            bodies[index] = rule(code, code, (index + 1) * 10, reason, "超出档位额度", action("DENY_PAYMENT"), List.of(),
                rules[index]);
        }
        return frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode(prefix)).pointCode(PAYMENT).rules(bodies).build());
    }

    /** 支付币种不在档位币种集合。 */
    private static ConditionNodeRequestBody[] notIn(String currencySet) {
        return new ConditionNodeRequestBody[] {predicate("root", null, 1, "NOT_IN",
            variableOperand(TierQuotaVariableBuilder.PAY_CURRENCY, "STRING"), reference(currencySet, "STRING"))};
    }

    /** 支付币种为 {CCY} 且金额大于单笔常量。 */
    private static ConditionNodeRequestBody[] single(String currency, String constant) {
        return currencyAnd(currency, predicate("amount", "root", 2, "GT", variableOperand("PAY_INPUT_AMOUNT", "NUMBER"),
            reference(constant, "NUMBER")));
    }

    /** 支付币种为 {CCY} 且放行累计（含本次）大于累计常量。 */
    private static ConditionNodeRequestBody[] accumulated(String currency, String velocity, String constant) {
        return currencyAnd(currency, predicate("sum", "root", 2, "GT", variableOperand(velocity, "NUMBER"),
            reference(constant, "NUMBER")));
    }

    /** “支付币种 = {CCY}”与给定比较的 AND 组。 */
    private static ConditionNodeRequestBody[] currencyAnd(String currency, ConditionNodeRequestBody comparison) {
        return new ConditionNodeRequestBody[] {group("root", null, 1, "AND"),
            predicate("currency", "root", 1, "EQ", variableOperand(TierQuotaVariableBuilder.PAY_CURRENCY, "STRING"),
                literal("RIGHT", 1, "STRING", currency)),
            comparison};
    }

    /** 右侧变量引用。 */
    private static OperandRequestBody reference(String code, String valueType) {
        return new OperandRequestBody("RIGHT", 1, "VARIABLE", null, valueType, null, code, null, null, null);
    }

    /**
     * 档位分支流程：START 只能有一条无条件出口，先进入档位分支节点（只含无终态动作的规则集），由它按 RESTRICTED、VERIFICATION、
     * OBSERVATION、STANDARD 名单依次分支，ELSE 无档位拒绝；三个额度规则集节点拒绝走额度拒绝，否则放行
     *
     * @param ruleSets 档位分支节点、VERIFICATION、OBSERVATION、STANDARD 规则集版本
     * @param tiers RESTRICTED、VERIFICATION、OBSERVATION、STANDARD 名单命中变量
     * @param reason 拒绝原因编码
     * @return 配置图
     */
    private static GraphSpec tierGraph(long[] ruleSets, String[] tiers, String reason) {
        return new GraphSpec(List.of(
            new NodeSpec("start", FlowNodeType.START, "Start", 0, 0, null, null, null, null),
            new NodeSpec("gate", FlowNodeType.RULE_SET, "Tier gate", 60, 0, ruleSets[0], null, null, null),
            new NodeSpec("verification", FlowNodeType.RULE_SET, "Verification", 120, -120, ruleSets[1], null, null, null),
            new NodeSpec("observation", FlowNodeType.RULE_SET, "Observation", 120, 0, ruleSets[2], null, null, null),
            new NodeSpec("standard", FlowNodeType.RULE_SET, "Standard", 120, 120, ruleSets[3], null, null, null),
            new NodeSpec("deny_restricted", FlowNodeType.END, "Restricted", 240, -240, null, DecisionValue.DENY, reason,
                "受限档位"),
            new NodeSpec("deny_not_set", FlowNodeType.END, "No tier", 240, 240, null, DecisionValue.DENY, reason,
                "无交易额度"),
            new NodeSpec("deny_limit", FlowNodeType.END, "Limit", 360, -120, null, DecisionValue.DENY, reason, "超出额度"),
            new NodeSpec("allow", FlowNodeType.END, "Allow", 360, 0, null, DecisionValue.ALLOW, null, null)),
            List.of(new EdgeSpec("enter", "start", "gate", FlowEdgeType.UNCONDITIONAL, 0, "Enter", null),
                new EdgeSpec("to_restricted", "gate", "deny_restricted", FlowEdgeType.CONDITIONAL, 1, "Restricted",
                    listHit(tiers[0])),
                new EdgeSpec("to_verification", "gate", "verification", FlowEdgeType.CONDITIONAL, 2, "Verification",
                    listHit(tiers[1])),
                new EdgeSpec("to_observation", "gate", "observation", FlowEdgeType.CONDITIONAL, 3, "Observation",
                    listHit(tiers[2])),
                new EdgeSpec("to_standard", "gate", "standard", FlowEdgeType.CONDITIONAL, 4, "Standard", listHit(tiers[3])),
                new EdgeSpec("to_not_set", "gate", "deny_not_set", FlowEdgeType.ELSE, 0, "No tier", null),
                limitEdge("v", "verification"), allowEdge("v", "verification"),
                limitEdge("o", "observation"), allowEdge("o", "observation"),
                limitEdge("s", "standard"), allowEdge("s", "standard")));
    }

    /** 额度规则集拒绝走额度拒绝。 */
    private static EdgeSpec limitEdge(String key, String from) {
        return new EdgeSpec(key + "_limit", from, "deny_limit", FlowEdgeType.CONDITIONAL, 1, "Limit",
            nodeDecision(DecisionValue.DENY));
    }

    /** 额度规则集未拒绝走放行。 */
    private static EdgeSpec allowEdge(String key, String from) {
        return new EdgeSpec(key + "_allow", from, "allow", FlowEdgeType.ELSE, 0, "Within limit", null);
    }

    /** 流程连线条件：名单命中变量为 true。 */
    private static String listHit(String variableCode) {
        return "{\"root\":{\"kind\":\"PREDICATE\",\"operands\":["
            + "{\"currencyCode\":null,\"currencyVariableCode\":null,\"fieldCode\":null,\"literalValue\":null,"
            + "\"numericUnit\":null,\"operandRole\":\"LEFT\",\"sourceType\":\"VARIABLE\",\"valueSequence\":1,"
            + "\"valueType\":\"BOOLEAN\",\"variableCode\":\"" + variableCode + "\"},"
            + "{\"currencyCode\":null,\"currencyVariableCode\":null,\"fieldCode\":null,\"literalValue\":true,"
            + "\"numericUnit\":null,\"operandRole\":\"RIGHT\",\"sourceType\":\"LITERAL\",\"valueSequence\":1,"
            + "\"valueType\":\"BOOLEAN\",\"variableCode\":null}],\"operator\":\"EQ\"},\"schemaVersion\":2}";
    }
}
