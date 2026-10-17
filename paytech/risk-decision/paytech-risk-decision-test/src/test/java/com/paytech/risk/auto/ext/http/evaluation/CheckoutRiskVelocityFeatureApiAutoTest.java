package com.paytech.risk.auto.ext.http.evaluation;

import static com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.decision.api.DecisionRuleSetManagementModels.RuleRequestBody;
import com.paytech.risk.decision.api.evaluation.RiskDecisionApi;
import com.paytech.risk.decision.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.control.core.enums.RiskListPurpose;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;
import com.paytech.risk.decision.domain.enums.VelocityMetric;
import com.paytech.risk.test.base.AbstractCheckoutRiskApiTest;
import com.paytech.risk.test.builder.decision.RuleSetCreateRequestBuilder;
import com.paytech.risk.test.builder.evaluation.CheckoutRiskRequestBuilder;
import com.paytech.risk.test.checker.evaluation.EvaluationResponseChecker.Assessed;

import tools.jackson.databind.JsonNode;

/**
 * 结账点实时累计与名单命中：经真实配置入口创建结账 Feature 变量、规则集与生效发布，再经统一评估入口计算
 * <li>窗口为 [评估时间 - 窗口, 评估时间)；Client、运营主体为锚定主体；金额为税前净额，只合计与本次同币种的事件。</li>
 * <li>结账累计只读结账事件：同一 Client、运营主体的支付事件不计入。</li>
 * <li>业务规则：10 分钟内被拦截 3 次以上拒绝；Client 或运营主体在封禁名单中拒绝。</li>
 * <li>连续评估：每次成功评估写入的事件被下一次评估计入；评估解释显示 Feature 取值。</li>
 *
 * @see RiskDecisionApi#evaluate(EvaluateDecisionRequestBody)
 */
class CheckoutRiskVelocityFeatureApiAutoTest extends AbstractCheckoutRiskApiTest {
    /** 模板订单的 Client。 */
    private static final String CLIENT = "800001";

    /** 模板订单的运营主体。 */
    private static final String ENTITY = "AE001";

    /** 封禁名单中的运营主体。 */
    private static final String BLOCKED_ENTITY = "AE002";

    /** 历史事件 ID 起点；每例结束按结账来源清理。 */
    private static final long EVENT_BASE = 7_200_000_000L;

    private final Map<String, String> variables = new LinkedHashMap<>();

    private String clientList;

    private String entityList;

    /** 每例创建结账累计与名单变量、引用它们的规则集与生效发布。 */
    @BeforeEach
    void setUpClassMocks() {
        variables.clear();
        VelocityEventCode assessed = VelocityEventCode.CHECKOUT_ASSESSED;
        velocity("clientCount10m", assessed, RiskSubjectType.CLIENT, VelocityMetric.COUNT, 600, false);
        velocity("clientCountWithCurrent10m", assessed, RiskSubjectType.CLIENT, VelocityMetric.COUNT, 600, true);
        velocity("clientCount1d", assessed, RiskSubjectType.CLIENT, VelocityMetric.COUNT, 86_400, false);
        velocity("entitySum1h", assessed, RiskSubjectType.OPERATING_ENTITY, VelocityMetric.SUM_AMOUNT, 3600, false);
        velocity("entitySumWithCurrent1h", assessed, RiskSubjectType.OPERATING_ENTITY, VelocityMetric.SUM_AMOUNT, 3600,
            true);
        velocity("clientLastAmount1d", assessed, RiskSubjectType.CLIENT, VelocityMetric.LAST_AMOUNT, 86_400, false);
        velocity("clientSecondsSinceLast1d", assessed, RiskSubjectType.CLIENT, VelocityMetric.SECONDS_SINCE_LAST, 86_400,
            false);
        velocity("clientRejected10m", VelocityEventCode.CHECKOUT_REJECTED, RiskSubjectType.CLIENT, VelocityMetric.COUNT,
            600, false);
        clientList = checkoutCode("CLIENT_LIST");
        entityList = checkoutCode("ENTITY_LIST");
        createList(clientList, RiskSubjectType.CLIENT.getCode(), RiskListPurpose.BLOCK.getCode());
        createList(entityList, RiskSubjectType.OPERATING_ENTITY.getCode(), RiskListPurpose.BLOCK.getCode());
        addListEntry(entityList, BLOCKED_ENTITY);
        String clientBlocked = checkoutCode("CLIENT_BLOCKED");
        String entityBlocked = checkoutCode("ENTITY_BLOCKED");
        createListVariable(clientBlocked, clientList, RiskSubjectType.CLIENT.getCode());
        createListVariable(entityBlocked, entityList, RiskSubjectType.OPERATING_ENTITY.getCode());
        variables.put("clientBlocked", clientBlocked);
        variables.put("entityBlocked", entityBlocked);
        activeRelease(saveFlow(checkoutCode("FLOW"), blockGraph(blockRuleSet(), rejectionReason())));
    }

    /** 窗口边界与五种口径；支付事件与 EUR 事件不计入。 */
    @Test
    void test_normalFlow() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        history(1, VelocityEventCode.CHECKOUT_ASSESSED, now.minusSeconds(60), "10", "USD");
        history(2, VelocityEventCode.CHECKOUT_ASSESSED, now.minusSeconds(590), "20", "USD");
        history(3, VelocityEventCode.CHECKOUT_ASSESSED, now.minusSeconds(610), "40", "USD");
        history(4, VelocityEventCode.CHECKOUT_ASSESSED, now.minusSeconds(1000), "5", "EUR");
        history(5, VelocityEventCode.CHECKOUT_ASSESSED, now.minusSeconds(3610), "80", "USD");
        history(6, VelocityEventCode.CHECKOUT_ASSESSED, now.minusSeconds(86_410), "160", "USD");
        history(7, VelocityEventCode.CHECKOUT_REJECTED, now.minusSeconds(100), "10", "USD");
        history(8, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED, now.minusSeconds(30), "999", "USD");

        EvaluateDecisionRequestBody request = CheckoutRiskRequestBuilder.template().build();
        Assessed result = assess(request).assertResult(DecisionValue.ALLOW.getCode());
        Map<String, Object> values = values(result.evaluationId());

        BigDecimal seconds = (BigDecimal)values.get("clientSecondsSinceLast1d");
        assertAll("累计值",
            () -> assertNumber("2", values.get("clientCount10m"), "10 分钟窗口只含 60 秒、590 秒两笔，不含支付事件"),
            () -> assertNumber("3", values.get("clientCountWithCurrent10m"), "计入本次"),
            () -> assertNumber("5", values.get("clientCount1d"), "1 天窗口不含 86410 秒前"),
            () -> assertNumber("70", values.get("entitySum1h"), "只合计 USD 且不含 1 小时外"),
            () -> assertNumber("170", values.get("entitySumWithCurrent1h"), "合计计入本次净额 100"),
            () -> assertNumber("10", values.get("clientLastAmount1d"), "最近一次结账评估金额，不取支付事件"),
            () -> assertTrue(seconds.compareTo(new BigDecimal("60")) >= 0 && seconds.compareTo(new BigDecimal("70")) < 0,
                "距最近一次秒数: " + seconds),
            () -> assertNumber("1", values.get("clientRejected10m"), "拦截事件"),
            () -> assertEquals(Boolean.FALSE, values.get("clientBlocked"), "Client 不在名单"),
            () -> assertEquals(Boolean.FALSE, values.get("entityBlocked"), "运营主体不在名单"));

        JsonNode explanation = explanation(result.evaluationId());
        assertAll("解释",
            () -> assertEquals("2", variableOf(explanation, variables.get("clientCount10m")).path("value").asString()),
            () -> assertEquals("EVALUATION",
                variableOf(explanation, variables.get("clientCount10m")).path("valueBasis").asString()),
            () -> assertEquals("170",
                variableOf(explanation, variables.get("entitySumWithCurrent1h")).path("value").asString()),
            () -> assertEquals("false",
                variableOf(explanation, variables.get("clientBlocked")).path("value").asString()));
    }

    /** 同一 Client 10 分钟内被拦截 3 次：拒绝；其他 Client 不受影响。 */
    @Test
    void test_businessRuleViolation_rejectedRepeat() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        history(1, VelocityEventCode.CHECKOUT_REJECTED, now.minusSeconds(60), "10", "USD");
        history(2, VelocityEventCode.CHECKOUT_REJECTED, now.minusSeconds(120), "10", "USD");
        history(3, VelocityEventCode.CHECKOUT_REJECTED, now.minusSeconds(180), "10", "USD");

        assess(CheckoutRiskRequestBuilder.template().build()).assertResult(DecisionValue.DENY.getCode());
        assess(CheckoutRiskRequestBuilder.template().input("clientId", "800002").build())
            .assertResult(DecisionValue.ALLOW.getCode());
    }

    /** Client 加入封禁名单后拒绝；运营主体在封禁名单中拒绝。 */
    @Test
    void test_businessRuleViolation_blockedSubject() {
        assess(CheckoutRiskRequestBuilder.template().build()).assertResult(DecisionValue.ALLOW.getCode());
        addListEntry(clientList, CLIENT);

        assess(CheckoutRiskRequestBuilder.template().build()).assertResult(DecisionValue.DENY.getCode());
        Assessed entity = assess(CheckoutRiskRequestBuilder.template().input("clientId", "800002")
            .entityNo(BLOCKED_ENTITY).build()).assertResult(DecisionValue.DENY.getCode());
        assertEquals(Boolean.TRUE, values(entity.evaluationId()).get("entityBlocked"));
    }

    /** 连续评估：前两次写入的结账评估事件被第三次计入，金额为净额。 */
    @Test
    void test_edgeCase_liveAccumulation() {
        assess(CheckoutRiskRequestBuilder.template().amounts("50", "0", "50").build())
            .assertResult(DecisionValue.ALLOW.getCode());
        assess(CheckoutRiskRequestBuilder.template().amounts("60", "30", "30").build())
            .assertResult(DecisionValue.ALLOW.getCode());

        Assessed third = assess(CheckoutRiskRequestBuilder.template().build()).assertResult(DecisionValue.ALLOW.getCode());

        Map<String, Object> values = values(third.evaluationId());
        assertAll(() -> assertNumber("2", values.get("clientCount10m"), "前两次评估"),
            () -> assertNumber("80", values.get("entitySum1h"), "净额 50 + 30"),
            () -> assertNumber("30", values.get("clientLastAmount1d"), "最近一次净额"),
            () -> assertNumber("0", values.get("clientRejected10m"), "均为放行"));
    }

    /** 拦截规则集：被拦截 3 次以上或名单命中拒绝；累计值超过极大阈值审核（正常不命中），使每个累计变量都被计算。 */
    private long blockRuleSet() {
        String reason = rejectionReason();
        List<RuleRequestBody> rules = new ArrayList<>();
        rules.add(rule("REJECTED_REPEAT", "REJECTED_REPEAT", 10, reason, DENY_MESSAGE, action(DENY), List.of(),
            predicate("root", null, 1, ComparisonOperator.GTE.getCode(), variableOperand(variables.get("clientRejected10m"), DecisionValueType.NUMBER.getCode()), scalar("3"))));
        rules.add(rule("CLIENT_BLOCKED", "CLIENT_BLOCKED", 20, reason, DENY_MESSAGE, action(DENY), List.of(),
            predicate("root", null, 1, ComparisonOperator.EQ.getCode(), variableOperand(variables.get("clientBlocked"), DecisionValueType.BOOLEAN.getCode()), bool(true))));
        rules.add(rule("ENTITY_BLOCKED", "ENTITY_BLOCKED", 30, reason, DENY_MESSAGE, action(DENY), List.of(),
            predicate("root", null, 1, ComparisonOperator.EQ.getCode(), variableOperand(variables.get("entityBlocked"), DecisionValueType.BOOLEAN.getCode()), bool(true))));
        int priority = 40;
        for (Map.Entry<String, String> variable : variables.entrySet()) {
            if (variable.getKey().endsWith("Blocked")) {
                continue;
            }
            boolean money = variable.getKey().contains("Sum") || variable.getKey().contains("LastAmount");
            rules.add(rule(variable.getKey().toUpperCase(), variable.getKey(), priority, action(REVIEW),
                predicate("root", null, 1, ComparisonOperator.GTE.getCode(), variableOperand(variable.getValue(), DecisionValueType.NUMBER.getCode()),
                    money ? money("1000000") : scalar("1000000"))));
            priority += 10;
        }
        return frozenRuleSet(RuleSetCreateRequestBuilder.template(checkoutCode("BLOCK")).pointCode(CHECKOUT)
            .rules(rules.toArray(RuleRequestBody[]::new)).build());
    }

    /** 创建结账累计变量并记录名称到变量编码。 */
    private void velocity(String name, VelocityEventCode event, RiskSubjectType anchor, VelocityMetric metric,
        int windowSeconds, boolean includeCurrent) {
        String code = checkoutCode(name.toUpperCase());
        createVelocityVariable(code, event.getCode(), anchor.getCode(), metric.getCode(), null, windowSeconds,
            includeCurrent);
        variables.put(name, code);
    }

    /** 插入一笔锚定模板 Client 与运营主体的历史事件。 */
    private void history(int sequence, VelocityEventCode eventCode, Instant occurred, String amount, String currency) {
        Map<String, String> subjects = new LinkedHashMap<>();
        subjects.put(RiskSubjectType.CLIENT.getCode(), CLIENT);
        subjects.put(RiskSubjectType.OPERATING_ENTITY.getCode(), ENTITY);
        store.insertVelocityEvent(EVENT_BASE + sequence, eventCode.getCode(), occurred, new BigDecimal(amount), currency,
            subjects, (EVENT_BASE + sequence) * 10);
    }

    /** 按变量名读取本次落库的 Feature 值：累计为数值，名单为布尔。 */
    private Map<String, Object> values(long evaluationId) {
        Map<Long, Map<String, Object>> byFeature = new HashMap<>();
        store.featureResults(evaluationId)
            .forEach(row -> byFeature.put(((Number)row.get("feature_version_id")).longValue(), row));
        Map<String, Object> values = new HashMap<>();
        variables.forEach((name, code) -> {
            boolean list = name.endsWith("Blocked");
            long featureVersionId = ((Number)(list ? store.listFeature(code) : store.velocityFeature(code))
                .get("feature_version_id")).longValue();
            Map<String, Object> row = byFeature.get(featureVersionId);
            assertNotNull(row, "Feature 结果: " + name);
            values.put(name, list ? Boolean.valueOf(Boolean.TRUE.equals(row.get("boolean_value"))) : row.get("number_value"));
        });
        return values;
    }

    /** 读取评估解释。 */
    private JsonNode explanation(long evaluationId) {
        HttpHeaders headers = TestRequestHeaders.internal("risk-checkout-velocity-" + UUID.randomUUID());
        EntityExchangeResult<byte[]> result = http.get(ASSESSMENTS + "/" + evaluationId + "/explanation", headers);
        assertEquals(200, result.getStatus().value(), () -> new String(result.getResponseBody()));
        return objectMapper.readTree(result.getResponseBody());
    }

    /** 解释中的变量。 */
    private static JsonNode variableOf(JsonNode explanation, String code) {
        for (JsonNode item : explanation.path("variables")) {
            if (code.equals(item.path("variableCode").asString())) {
                return item;
            }
        }
        return fail("解释中没有变量: " + code);
    }

    private static void assertNumber(String expected, Object actual, String message) {
        assertNotNull(actual, message);
        assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal)actual), message + ": " + actual);
    }
}
