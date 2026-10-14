package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.risk.api.enums.RiskVariableErrorCode.INVALID_VARIABLE_DEFINITION;
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
import com.paytech.risk.api.decision.RiskVariableApi;
import com.paytech.risk.api.decision.request.CreateVariableRequestBody;
import com.paytech.risk.api.decision.request.ListFeatureParameters;
import com.paytech.risk.api.decision.request.VelocityFeatureParameters;
import com.paytech.risk.control.core.enums.RiskListPurpose;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.domain.enums.ExecutionVersionStatus;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;
import com.paytech.risk.decision.domain.enums.VelocityMetric;
import com.paytech.risk.decision.domain.feature.FeatureHandlers;
import com.paytech.risk.test.base.AbstractPaymentRiskApiTest;
import com.paytech.risk.test.checker.decision.VariableDetailResponseChecker;

/**
 * 创建 Feature 变量：运营经变量创建入口配置实时累计与名单命中参数，随不可变 Feature Version 保存
 * <li>正常流程：计数、去重计数、金额合计（计入本次）的累计变量与名单命中变量各生成一个生效 Feature Version 及参数行。</li>
 * <li>参数：窗口不在允许集合、去重主体与算法不匹配或等于锚定主体、拒绝事件计入本次、量纲与算法不一致、来源与参数不匹配、名单不存在或主体类型不一致返回 400，且不写 Feature Version。</li>
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

    /** 同 Client 1 小时内不同卡数量；同消费者 1 天内评估金额合计且计入本次。 */
    @Test
    void test_normalFlow_distinctAndMoney() {
        String distinct = newCode("T_CLIENT_CARDS");
        String amount = newCode("T_CONSUMER_AMOUNT");

        createVelocityVariable(distinct, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED.getCode(),
            RiskSubjectType.CLIENT.getCode(), VelocityMetric.DISTINCT_COUNT.getCode(),
            RiskSubjectType.CARD_FINGERPRINT.getCode(), 3600, false);
        createVelocityVariable(amount, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED.getCode(),
            RiskSubjectType.CONSUMER.getCode(), VelocityMetric.SUM_AMOUNT.getCode(), null, 86_400, true);

        assertVelocity(distinct, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED, RiskSubjectType.CLIENT,
            VelocityMetric.DISTINCT_COUNT, RiskSubjectType.CARD_FINGERPRINT, 3600, false);
        assertVelocity(amount, VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED, RiskSubjectType.CONSUMER,
            VelocityMetric.SUM_AMOUNT, null, 86_400, true);
        assertEquals(2, store.countFeatureVersions());
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
            })));
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
