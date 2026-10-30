package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.risk.decision.api.enums.RiskVariableErrorCode.INVALID_VARIABLE_DEFINITION;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.risk.decision.api.RiskVariableApi;
import com.paytech.risk.decision.api.request.CreateVariableRequestBody;
import com.paytech.risk.decision.api.request.ListFeatureParameters;
import com.paytech.risk.decision.api.request.VelocityFeatureParameters;
import com.paytech.risk.decision.api.evaluation.request.CheckoutPrepareRiskInput;
import com.paytech.risk.control.core.enums.RiskListPurpose;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.ExecutionVersionStatus;
import com.paytech.risk.decision.domain.enums.NumericUnit;
import com.paytech.risk.decision.domain.enums.VariableSource;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;
import com.paytech.risk.decision.domain.enums.VelocityMetric;
import com.paytech.risk.decision.domain.feature.FeatureHandlers;
import com.paytech.risk.test.base.AbstractPaymentRiskApiTest;
import com.paytech.risk.test.checker.decision.VariableDetailResponseChecker;
import com.paytech.risk.test.mapper.evaluation.CheckoutRiskTestMapper;

/**
 * 创建 Feature 变量：运营经变量创建入口配置实时累计与名单命中参数，随不可变 Feature Version 保存
 * <li>正常流程：计数、去重计数、金额合计（计入本次）的累计变量与名单命中变量各生成一个生效 Feature Version 及参数行；结账点以 Client、运营主体创建累计与名单变量。</li>
 * <li>参数：窗口不在允许集合、去重主体与算法不匹配或等于锚定主体、拒绝事件计入本次、量纲与算法不一致、来源与参数不匹配、名单不存在或主体类型不一致、
 * 事件或主体不属于变量风控点、Client/运营主体超出允许的统计方式与窗口、结账金额变量绑定支付币种返回 400，且不写 Feature Version。</li>
 *
 * @see RiskVariableApi
 */
class FeatureVariableCreateApiAutoTest extends AbstractPaymentRiskApiTest {
    private static final String VARIABLES = "/decision-variables/v1/variables";

    private String ipList;

    /** 每例新建一个 IP 黑名单。 */
    @BeforeEach
    void setUpClassMocks() {
        ipList = newCode("T_IP_LIST");
        createList(ipList, RiskSubjectType.IP_ADDRESS.getCode(), RiskListPurpose.BLOCK.getCode());
    }

    /** 同卡 10 分钟评估次数。 */
    @Test
    void test_normalFlow_count() {
        String code = newCode("T_CARD_COUNT");

        createVelocityVariable(code, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED.getCode(),
            RiskSubjectType.CARD_FINGERPRINT.getCode(), VelocityMetric.COUNT.getCode(), null, 600, false);

        assertVelocity(code, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED, RiskSubjectType.CARD_FINGERPRINT,
            VelocityMetric.COUNT, null, 600, false);
    }

    /** 同消费者 1 小时内不同卡数量；同消费者 1 天内评估金额合计且计入本次。 */
    @Test
    void test_normalFlow_distinctAndMoney() {
        String distinct = newCode("T_CONSUMER_CARDS");
        String amount = newCode("T_CONSUMER_AMOUNT");

        createVelocityVariable(distinct, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED.getCode(),
            RiskSubjectType.CONSUMER.getCode(), VelocityMetric.DISTINCT_COUNT.getCode(),
            RiskSubjectType.CARD_FINGERPRINT.getCode(), 3600, false);
        createVelocityVariable(amount, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED.getCode(),
            RiskSubjectType.CONSUMER.getCode(), VelocityMetric.SUM_AMOUNT.getCode(), null, 86_400, true);

        assertVelocity(distinct, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED, RiskSubjectType.CONSUMER,
            VelocityMetric.DISTINCT_COUNT, RiskSubjectType.CARD_FINGERPRINT, 3600, false);
        assertVelocity(amount, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED, RiskSubjectType.CONSUMER,
            VelocityMetric.SUM_AMOUNT, null, 86_400, true);
        assertEquals(2, store.countFeatureVersions());
    }

    /** 同 Client 近 30 个 UTC 日放行金额（按日汇总，计入本次）与 UTC 当日放行金额。 */
    @Test
    void test_normalFlow_dailySum() {
        String month = newCode("T_CLIENT_ALLOWED_30D");
        String day = newCode("T_CLIENT_ALLOWED_DAY");

        createVelocityVariable(month, VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED.getCode(), RiskSubjectType.CLIENT.getCode(),
            VelocityMetric.DAILY_SUM_AMOUNT.getCode(), null, 2_592_000, true);
        createVelocityVariable(day, VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED.getCode(), RiskSubjectType.CLIENT.getCode(),
            VelocityMetric.DAILY_SUM_AMOUNT.getCode(), null, 86_400, false);

        assertVelocity(month, VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED, RiskSubjectType.CLIENT,
            VelocityMetric.DAILY_SUM_AMOUNT, null, 2_592_000, true);
        assertVelocity(day, VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED, RiskSubjectType.CLIENT,
            VelocityMetric.DAILY_SUM_AMOUNT, null, 86_400, false);
    }

    /** 结账点：同 Client 1 小时评估次数（计入本次）、同运营主体 1 小时净额合计、Client 名单命中。 */
    @Test
    void test_normalFlow_checkout() {
        String count = newCode(CheckoutRiskTestMapper.TEST_CODE_PREFIX + "_CLIENT_COUNT");
        String amount = newCode(CheckoutRiskTestMapper.TEST_CODE_PREFIX + "_OE_AMOUNT");
        String blocked = newCode(CheckoutRiskTestMapper.TEST_CODE_PREFIX + "_CLIENT_BLOCKED");
        String clientList = newCode(CheckoutRiskTestMapper.TEST_CODE_PREFIX + "_CLIENT_LIST");
        createList(clientList, RiskSubjectType.CLIENT.getCode(), RiskListPurpose.BLOCK.getCode());

        postOk(VARIABLES, checkoutVelocity(count, VelocityEventCode.CHECKOUT_ASSESSED, RiskSubjectType.CLIENT,
            VelocityMetric.COUNT, 3600, true));
        postOk(VARIABLES, checkoutVelocity(amount, VelocityEventCode.CHECKOUT_ASSESSED, RiskSubjectType.OPERATING_ENTITY,
            VelocityMetric.SUM_AMOUNT, 3600, false));
        CreateVariableRequestBody list = featureVariable(blocked, VariableSource.LIST.getCode(),
            DecisionValueType.BOOLEAN.getCode(), CheckoutPrepareRiskInput.POINT_CODE);
        list.setList(listParameters(clientList, RiskSubjectType.CLIENT));
        postOk(VARIABLES, list);

        assertVelocity(count, VelocityEventCode.CHECKOUT_ASSESSED, RiskSubjectType.CLIENT, VelocityMetric.COUNT, null,
            3600, true);
        assertVelocity(amount, VelocityEventCode.CHECKOUT_ASSESSED, RiskSubjectType.OPERATING_ENTITY,
            VelocityMetric.SUM_AMOUNT, null, 3600, false);
        assertEquals(RiskSubjectType.CLIENT.getCode(), store.listFeature(blocked).get("subject_type"));
    }

    /** IP 名单命中。 */
    @Test
    void test_normalFlow_list() {
        String code = newCode("T_IP_BLOCKED");

        createListVariable(code, ipList, RiskSubjectType.IP_ADDRESS.getCode());

        Map<String, Object> feature = store.listFeature(code);
        assertAll("名单 Feature",
            () -> assertEquals(FeatureHandlers.LIST_HANDLER, feature.get("handler_code")),
            () -> assertEquals(ExecutionVersionStatus.ACTIVE.getCode(), feature.get("status")),
            () -> assertEquals(RiskSubjectType.IP_ADDRESS.getCode(), feature.get("subject_type")));
    }

    /** Feature 参数与来源、算法、量纲或名单不一致。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDefinitions")
    void test_invalidParameter(String scenario, Consumer<CreateVariableRequestBody> change) {
        CreateVariableRequestBody body = velocityBody(VelocityMetric.COUNT, null, 600, false);
        change.accept(body);
        if (body.getList() != null && "{list}".equals(body.getList().getListCode())) {
            body.getList().setListCode(ipList);
        }

        post(body).assertProblem(400, INVALID_VARIABLE_DEFINITION.getCode());

        assertEquals(0, store.countFeatureVersions(), "不写 Feature Version");
    }

    /** 同时提供累计与名单参数。 */
    @Test
    void test_invalidParameter_bothFeatures() {
        CreateVariableRequestBody body = velocityBody(VelocityMetric.COUNT, null, 600, false);
        body.setList(listParameters(ipList, RiskSubjectType.IP_ADDRESS));

        post(body).assertProblem(400, INVALID_REQUEST.getCode());
    }

    static Stream<Arguments> invalidDefinitions() {
        return Stream.of(
            Arguments.of("窗口不在允许集合", change(body -> body.getVelocity().setWindowSeconds(1234))),
            Arguments.of("计数提供去重主体", change(body -> body.getVelocity()
                .setDistinctSubjectType(RiskSubjectType.CLIENT.getCode()))),
            Arguments.of("去重主体等于锚定主体", change(body -> {
                body.getVelocity().setMetric(VelocityMetric.DISTINCT_COUNT.getCode());
                body.getVelocity().setDistinctSubjectType(RiskSubjectType.CARD_FINGERPRINT.getCode());
            })),
            Arguments.of("拒绝事件计入本次", change(body -> {
                body.getVelocity().setEventCode(VelocityEventCode.PAYMENT_ATTEMPT_REJECTED.getCode());
                body.getVelocity().setIncludeCurrent(true);
            })),
            Arguments.of("金额合计使用普通量纲", change(body ->
                body.getVelocity().setMetric(VelocityMetric.SUM_AMOUNT.getCode()))),
            Arguments.of("累计来源缺少参数", change(body -> body.setVelocity(null))),
            Arguments.of("名单来源携带累计参数", change(body -> {
                body.setSourceType("LIST");
                body.setValueType("BOOLEAN");
                body.setNumericUnit(null);
            })),
            Arguments.of("名单不存在", change(body -> {
                body.setSourceType("LIST");
                body.setValueType("BOOLEAN");
                body.setNumericUnit(null);
                body.setVelocity(null);
                body.setList(listParameters("T_MISSING_LIST", RiskSubjectType.IP_ADDRESS));
            })),
            Arguments.of("名单主体类型不一致", change(body -> {
                body.setSourceType("LIST");
                body.setValueType("BOOLEAN");
                body.setNumericUnit(null);
                body.setVelocity(null);
                body.setList(listParameters("{list}", RiskSubjectType.CARD_BIN));
            })),
            Arguments.of("结账变量选支付事件", change(FeatureVariableCreateApiAutoTest::toCheckout)),
            Arguments.of("支付变量选结账事件", change(body ->
                body.getVelocity().setEventCode(VelocityEventCode.CHECKOUT_ASSESSED.getCode()))),
            Arguments.of("结账变量以卡指纹为主体", change(body -> {
                toCheckout(body);
                body.getVelocity().setEventCode(VelocityEventCode.CHECKOUT_ASSESSED.getCode());
            })),
            Arguments.of("结账名单主体为 IP", change(body -> {
                body.setSourceType("LIST");
                body.setValueType("BOOLEAN");
                body.setNumericUnit(null);
                body.setVelocity(null);
                body.setList(listParameters("{list}", RiskSubjectType.IP_ADDRESS));
                toCheckout(body);
            })),
            Arguments.of("支付 Client 7 天次数", change(body -> {
                body.getVelocity().setAnchorSubjectType(RiskSubjectType.CLIENT.getCode());
                body.getVelocity().setWindowSeconds(604_800);
            })),
            Arguments.of("支付运营主体 1 天金额合计", change(body -> {
                money(body, VelocityMetric.SUM_AMOUNT, DecisionPointAuthoringDescriptor.paymentAttemptRisk().currencyVariableCode());
                body.getVelocity().setAnchorSubjectType(RiskSubjectType.OPERATING_ENTITY.getCode());
                body.getVelocity().setWindowSeconds(86_400);
            })),
            Arguments.of("支付 Client 不同主体数", change(body -> {
                body.getVelocity().setAnchorSubjectType(RiskSubjectType.CLIENT.getCode());
                body.getVelocity().setMetric(VelocityMetric.DISTINCT_COUNT.getCode());
                body.getVelocity().setDistinctSubjectType(RiskSubjectType.CARD_FINGERPRINT.getCode());
            })),
            Arguments.of("支付 Client 1 天金额合计", change(body -> {
                money(body, VelocityMetric.SUM_AMOUNT, DecisionPointAuthoringDescriptor.paymentAttemptRisk().currencyVariableCode());
                body.getVelocity().setAnchorSubjectType(RiskSubjectType.CLIENT.getCode());
                body.getVelocity().setWindowSeconds(86_400);
            })),
            Arguments.of("结账 Client 1 天金额合计", change(body -> {
                checkout(body, VelocityEventCode.CHECKOUT_ASSESSED, RiskSubjectType.CLIENT);
                money(body, VelocityMetric.SUM_AMOUNT, DecisionPointAuthoringDescriptor.checkoutPrepare().currencyVariableCode());
                body.getVelocity().setWindowSeconds(86_400);
            })),
            Arguments.of("结账 Client 7 天最近一次金额", change(body -> {
                checkout(body, VelocityEventCode.CHECKOUT_ASSESSED, RiskSubjectType.CLIENT);
                money(body, VelocityMetric.LAST_AMOUNT, DecisionPointAuthoringDescriptor.checkoutPrepare().currencyVariableCode());
                body.getVelocity().setWindowSeconds(604_800);
            })),
            Arguments.of("结账 Client 7 天距上次秒数", change(body -> {
                checkout(body, VelocityEventCode.CHECKOUT_ASSESSED, RiskSubjectType.CLIENT);
                body.getVelocity().setMetric(VelocityMetric.SECONDS_SINCE_LAST.getCode());
                body.getVelocity().setWindowSeconds(604_800);
            })),
            Arguments.of("结账 Client 7 天次数", change(body -> {
                checkout(body, VelocityEventCode.CHECKOUT_ASSESSED, RiskSubjectType.CLIENT);
                body.getVelocity().setWindowSeconds(604_800);
            })),
            Arguments.of("结账运营主体不同 Client 数", change(body -> {
                checkout(body, VelocityEventCode.CHECKOUT_ASSESSED, RiskSubjectType.OPERATING_ENTITY);
                body.getVelocity().setMetric(VelocityMetric.DISTINCT_COUNT.getCode());
                body.getVelocity().setDistinctSubjectType(RiskSubjectType.CLIENT.getCode());
            })),
            Arguments.of("结账拦截事件计入本次", change(body -> {
                checkout(body, VelocityEventCode.CHECKOUT_REJECTED, RiskSubjectType.CLIENT);
                body.getVelocity().setIncludeCurrent(true);
            })),
            Arguments.of("按日汇总统计评估事件", change(body -> {
                money(body, VelocityMetric.DAILY_SUM_AMOUNT, DecisionPointAuthoringDescriptor.paymentAttemptRisk().currencyVariableCode());
                body.getVelocity().setAnchorSubjectType(RiskSubjectType.CLIENT.getCode());
                body.getVelocity().setWindowSeconds(86_400);
            })),
            Arguments.of("按日汇总锚定运营主体", change(body -> {
                money(body, VelocityMetric.DAILY_SUM_AMOUNT, DecisionPointAuthoringDescriptor.paymentAttemptRisk().currencyVariableCode());
                body.getVelocity().setEventCode(VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED.getCode());
                body.getVelocity().setAnchorSubjectType(RiskSubjectType.OPERATING_ENTITY.getCode());
                body.getVelocity().setWindowSeconds(86_400);
            })),
            Arguments.of("按日汇总 1 小时窗口", change(body -> {
                money(body, VelocityMetric.DAILY_SUM_AMOUNT, DecisionPointAuthoringDescriptor.paymentAttemptRisk().currencyVariableCode());
                body.getVelocity().setEventCode(VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED.getCode());
                body.getVelocity().setAnchorSubjectType(RiskSubjectType.CLIENT.getCode());
                body.getVelocity().setWindowSeconds(3600);
            })),
            Arguments.of("支付 Client 30 天金额合计（明细）", change(body -> {
                money(body, VelocityMetric.SUM_AMOUNT, DecisionPointAuthoringDescriptor.paymentAttemptRisk().currencyVariableCode());
                body.getVelocity().setEventCode(VelocityEventCode.PAYMENT_ATTEMPT_ALLOWED.getCode());
                body.getVelocity().setAnchorSubjectType(RiskSubjectType.CLIENT.getCode());
                body.getVelocity().setWindowSeconds(2_592_000);
            })),
            Arguments.of("结账金额变量绑定支付币种", change(body -> {
                checkout(body, VelocityEventCode.CHECKOUT_ASSESSED, RiskSubjectType.CLIENT);
                money(body, VelocityMetric.SUM_AMOUNT, DecisionPointAuthoringDescriptor.paymentAttemptRisk().currencyVariableCode());
                body.getVelocity().setWindowSeconds(3600);
            })));
    }

    /** 改为结账决策点的累计参数。 */
    private static void checkout(CreateVariableRequestBody body, VelocityEventCode event, RiskSubjectType anchor) {
        toCheckout(body);
        body.getVelocity().setEventCode(event.getCode());
        body.getVelocity().setAnchorSubjectType(anchor.getCode());
    }

    /** 改为结账决策点；编码改用结账测试前缀，误放行时也由结账清理器删除。 */
    private static void toCheckout(CreateVariableRequestBody body) {
        body.setPointCode(CheckoutPrepareRiskInput.POINT_CODE);
        body.setVariableCode(newCode(CheckoutRiskTestMapper.TEST_CODE_PREFIX + "_INVALID"));
    }

    /** 改为金额类统计方式并绑定币种变量。 */
    private static void money(CreateVariableRequestBody body, VelocityMetric metric, String currencyVariableCode) {
        body.setNumericUnit(NumericUnit.MONEY.getCode());
        body.setCurrencyVariableCode(currencyVariableCode);
        body.getVelocity().setMetric(metric.getCode());
    }

    /** 结账决策点累计变量请求；金额类绑定订单币种。 */
    private static CreateVariableRequestBody checkoutVelocity(String code, VelocityEventCode event,
        RiskSubjectType anchor, VelocityMetric metric, int windowSeconds, boolean includeCurrent) {
        boolean money = metric == VelocityMetric.SUM_AMOUNT || metric == VelocityMetric.LAST_AMOUNT;
        CreateVariableRequestBody body = featureVariable(code, VariableSource.VELOCITY.getCode(),
            DecisionValueType.NUMBER.getCode(), CheckoutPrepareRiskInput.POINT_CODE);
        body.setNumericUnit(money ? NumericUnit.MONEY.getCode() : NumericUnit.SCALAR.getCode());
        body.setCurrencyVariableCode(money ? DecisionPointAuthoringDescriptor.checkoutPrepare().currencyVariableCode() : null);
        VelocityFeatureParameters velocity = new VelocityFeatureParameters();
        velocity.setEventCode(event.getCode());
        velocity.setAnchorSubjectType(anchor.getCode());
        velocity.setMetric(metric.getCode());
        velocity.setWindowSeconds(windowSeconds);
        velocity.setIncludeCurrent(includeCurrent);
        body.setVelocity(velocity);
        return body;
    }

    /** 核对累计 Feature Version 与冻结参数。 */
    private void assertVelocity(String code, VelocityEventCode event, RiskSubjectType anchor, VelocityMetric metric,
        RiskSubjectType distinct, int windowSeconds, boolean includeCurrent) {
        Map<String, Object> feature = store.velocityFeature(code);
        assertAll("累计 Feature",
            () -> assertEquals(FeatureHandlers.VELOCITY_HANDLER, feature.get("handler_code")),
            () -> assertEquals(ExecutionVersionStatus.ACTIVE.getCode(), feature.get("status")),
            () -> assertEquals(event.getCode(), feature.get("event_code")),
            () -> assertEquals(anchor.getCode(), feature.get("anchor_subject_type")),
            () -> assertEquals(metric.getCode(), feature.get("metric")),
            () -> assertEquals(distinct == null ? null : distinct.getCode(), feature.get("distinct_subject_type")),
            () -> assertEquals(windowSeconds, ((Number)feature.get("window_seconds")).intValue()),
            () -> assertEquals(includeCurrent, feature.get("include_current")));
    }

    /** 同卡计数累计变量请求。 */
    private static CreateVariableRequestBody velocityBody(VelocityMetric metric, RiskSubjectType distinct,
        int windowSeconds, boolean includeCurrent) {
        CreateVariableRequestBody body = featureVariable(newCode("T_FEATURE"), "VELOCITY", "NUMBER");
        body.setNumericUnit("SCALAR");
        VelocityFeatureParameters velocity = new VelocityFeatureParameters();
        velocity.setEventCode(VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED.getCode());
        velocity.setAnchorSubjectType(RiskSubjectType.CARD_FINGERPRINT.getCode());
        velocity.setMetric(metric.getCode());
        velocity.setDistinctSubjectType(distinct == null ? null : distinct.getCode());
        velocity.setWindowSeconds(windowSeconds);
        velocity.setIncludeCurrent(includeCurrent);
        body.setVelocity(velocity);
        return body;
    }

    /** 名单命中参数。 */
    private static ListFeatureParameters listParameters(String listCode, RiskSubjectType subjectType) {
        ListFeatureParameters list = new ListFeatureParameters();
        list.setListCode(listCode);
        list.setSubjectType(subjectType.getCode());
        return list;
    }

    /** 参数化场景的请求改写，保留场景名可读。 */
    private static Consumer<CreateVariableRequestBody> change(Consumer<CreateVariableRequestBody> change) {
        return change;
    }

    /** 带可信操作者的创建请求。 */
    private VariableDetailResponseChecker post(CreateVariableRequestBody body) {
        HttpHeaders headers = headers();
        return new VariableDetailResponseChecker(http.post(VARIABLES, headers, body), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }
}
