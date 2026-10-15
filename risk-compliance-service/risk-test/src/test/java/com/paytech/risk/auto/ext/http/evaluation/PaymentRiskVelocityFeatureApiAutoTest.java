package com.paytech.risk.auto.ext.http.evaluation;

import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.RuleRequestBody;
import com.paytech.risk.api.evaluation.RiskDecisionApi;
import com.paytech.risk.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.domain.enums.AuthenticationRequirement;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;
import com.paytech.risk.decision.domain.enums.VelocityMetric;
import com.paytech.risk.test.base.AbstractPaymentRiskApiTest;
import com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder;
import com.paytech.risk.test.builder.evaluation.PaymentRiskRequestBuilder;
import com.paytech.risk.test.checker.evaluation.PaymentRiskResponseChecker.Assessed;

/**
 * 实时累计 Feature 的窗口与算法：经真实评估入口计算，按落库的 Feature 结果核对
 * <li>窗口为 [评估时间 - 窗口, 评估时间)：窗口内 10 秒的历史计入，窗口外 10 秒的不计入。</li>
 * <li>计数、计入本次的计数、按支付币种的金额合计（含计入本次）、按 IP 去重计数、最近一次金额、距最近一次秒数、拒绝事件计数。</li>
 * <li>本次没有锚定主体（无卡指纹）时累计值为空。</li>
 *
 * @see RiskDecisionApi#evaluate(EvaluateDecisionRequestBody)
 */
class PaymentRiskVelocityFeatureApiAutoTest extends AbstractPaymentRiskApiTest {
    /** 历史事件锚定的卡指纹。 */
    private static final String CARD = "fp_window_card_0001";

    /** 历史事件 ID 起点；每例结束整表清理。 */
    private static final long EVENT_BASE = 7_100_000_000L;

    private final Map<String, String> variables = new LinkedHashMap<>();

    /** 每例创建八个累计变量、引用它们的规则集与生效发布。 */
    @BeforeEach
    void setUpClassMocks() {
        String assessed = VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED.getCode();
        String card = RiskSubjectType.CARD_FINGERPRINT.getCode();
        variables.clear();
        velocity("count10m", assessed, card, VelocityMetric.COUNT, null, 600, false);
        velocity("countWithCurrent", assessed, card, VelocityMetric.COUNT, null, 600, true);
        velocity("sum1d", assessed, card, VelocityMetric.SUM_AMOUNT, null, 86_400, false);
        velocity("sumWithCurrent1d", assessed, card, VelocityMetric.SUM_AMOUNT, null, 86_400, true);
        velocity("distinctIp1h", assessed, card, VelocityMetric.DISTINCT_COUNT, RiskSubjectType.IP_ADDRESS, 3600,
            false);
        velocity("lastAmount1h", assessed, card, VelocityMetric.LAST_AMOUNT, null, 3600, false);
        velocity("secondsSinceLast1h", assessed, card, VelocityMetric.SECONDS_SINCE_LAST, null, 3600, false);
        velocity("rejected10m", VelocityEventCode.PAYMENT_ATTEMPT_REJECTED.getCode(), card, VelocityMetric.COUNT, null,
            600, false);
        List<RuleRequestBody> rules = new ArrayList<>();
        int priority = 10;
        for (Map.Entry<String, String> variable : variables.entrySet()) {
            boolean money = variable.getKey().startsWith("sum") || variable.getKey().equals("lastAmount1h");
            rules.add(rule(variable.getKey().toUpperCase(), variable.getKey(), priority, action("REVIEW_PAYMENT"),
                predicate("root", null, 1, "GTE", variableOperand(variable.getValue(), "NUMBER"),
                    money ? money("1000000") : scalar("1000000"))));
            priority += 10;
        }
        String reason = rejectionReason();
        long block = frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_BLOCK")).pointCode(PAYMENT)
            .rules(rules.toArray(RuleRequestBody[]::new)).build());
        long auth = frozenRuleSet(RuleSetCreateRequestBuilder.template(newCode("T_AUTH")).pointCode(PAYMENT)
            .rules(rule("THREE_DS_AMOUNT", "3DS amount", 10, action("ALLOW_PAYMENT"),
                predicate("root", null, 1, "GT", variableOperand("PAY_INPUT_AMOUNT", "NUMBER"), money("500"))))
            .build());
        activeRelease(saveFlow(newCode("T_FLOW"), twoStageGraph(block, auth, reason)));
    }

    /** 窗口边界与七种累计口径。 */
    @Test
    void test_normalFlow() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        history(1, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED, now.minusSeconds(60), "10", "USD", "203.0.113.1");
        history(2, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED, now.minusSeconds(590), "20", "USD", "203.0.113.2");
        history(3, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED, now.minusSeconds(610), "40", "USD", "203.0.113.1");
        history(4, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED, now.minusSeconds(1000), "5", "EUR", "203.0.113.3");
        history(5, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED, now.minusSeconds(86_410), "80", "USD", "203.0.113.4");
        history(6, VelocityEventCode.PAYMENT_ATTEMPT_REJECTED, now.minusSeconds(100), "10", "USD", "203.0.113.1");

        Map<String, BigDecimal> values =
            assessValues(PaymentRiskRequestBuilder.template().cardFingerprint(CARD).build());

        BigDecimal seconds = values.get("secondsSinceLast1h");
        assertAll("累计值",
            () -> assertEquals(0, new BigDecimal("2").compareTo(values.get("count10m")), "10 分钟窗口只含 60 秒、590 秒两笔"),
            () -> assertEquals(0, new BigDecimal("3").compareTo(values.get("countWithCurrent")), "计入本次"),
            () -> assertEquals(0, new BigDecimal("70").compareTo(values.get("sum1d")), "只合计 USD 且不含 1 天外"),
            () -> assertEquals(0, new BigDecimal("170").compareTo(values.get("sumWithCurrent1d")), "合计计入本次 100"),
            () -> assertEquals(0, new BigDecimal("3").compareTo(values.get("distinctIp1h")), "1 小时内三个 IP"),
            () -> assertEquals(0, new BigDecimal("10").compareTo(values.get("lastAmount1h")), "最近一次金额"),
            () -> assertTrue(
                seconds.compareTo(new BigDecimal("60")) >= 0 && seconds.compareTo(new BigDecimal("70")) < 0,
                "距最近一次秒数: " + seconds),
            () -> assertEquals(0, BigDecimal.ONE.compareTo(values.get("rejected10m")), "拒绝事件"));
    }

    /** 最近一次事件币种与本次不同：最近一次金额为空，不跨币种取值。 */
    @Test
    void test_edgeCase_lastAmountOtherCurrency() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        history(1, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED, now.minusSeconds(300), "10", "USD", "203.0.113.1");
        history(2, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED, now.minusSeconds(60), "5", "EUR", "203.0.113.1");

        Map<String, BigDecimal> values =
            assessValues(PaymentRiskRequestBuilder.template().cardFingerprint(CARD).build());

        assertAll(() -> assertNull(values.get("lastAmount1h")),
            () -> assertEquals(0, new BigDecimal("10").compareTo(values.get("sum1d"))));
    }

    /** 本次没有卡指纹：锚定卡的累计值全部为空，评估照常放行。 */
    @Test
    void test_edgeCase_noAnchorSubject() {
        history(1, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED, Instant.now().minusSeconds(60), "10", "USD",
            "203.0.113.1");

        Map<String, BigDecimal> values =
            assessValues(PaymentRiskRequestBuilder.template().cardFingerprint(null).build());

        assertEquals(variables.keySet(), values.keySet());
        values.forEach((name, value) -> assertNull(value, name));
    }

    /** 创建累计变量并记录名称到变量编码。 */
    private void velocity(String name, String eventCode, String anchor, VelocityMetric metric, RiskSubjectType distinct,
        int windowSeconds, boolean includeCurrent) {
        String code = newCode("T_" + name.toUpperCase());
        createVelocityVariable(code, eventCode, anchor, metric.getCode(), distinct == null ? null : distinct.getCode(),
            windowSeconds, includeCurrent);
        variables.put(name, code);
    }

    /** 插入一笔锚定同一张卡的历史事件。 */
    private void history(int sequence, VelocityEventCode eventCode, Instant occurred, String amount, String currency,
        String ip) {
        Map<String, String> subjects = new LinkedHashMap<>();
        subjects.put(RiskSubjectType.CARD_FINGERPRINT.getCode(), CARD);
        subjects.put(RiskSubjectType.IP_ADDRESS.getCode(), ip);
        store.insertVelocityEvent(EVENT_BASE + sequence, eventCode.getCode(), occurred, new BigDecimal(amount),
            currency, subjects, (EVENT_BASE + sequence) * 10);
    }

    /** 放行评估后按变量名读取本次落库的累计值。 */
    private Map<String, BigDecimal> assessValues(EvaluateDecisionRequestBody request) {
        Assessed assessed = assess(request).assertResult(DecisionValue.ALLOW.getCode(),
            AuthenticationRequirement.NOT_REQUIRED.getCode());
        Map<Long, BigDecimal> byFeature = new HashMap<>();
        store.featureResults(assessed.evaluationId()).forEach(row -> byFeature
            .put(((Number)row.get("feature_version_id")).longValue(), (BigDecimal)row.get("number_value")));
        Map<String, BigDecimal> values = new HashMap<>();
        variables.forEach((name, code) -> values.put(name,
            byFeature.get(((Number)store.velocityFeature(code).get("feature_version_id")).longValue())));
        return values;
    }
}
