package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.risk.api.decision.RiskVariableApi;
import com.paytech.risk.test.base.AbstractVariableApiTest;
import com.paytech.risk.test.checker.decision.VariableAuthoringContextResponseChecker;

/**
 * 读取变量编排能力
 * <li>正常流程：缺省或指定已支持决策点时返回全部已支持点、来源能力、类型、格式、量纲、容量上限及准确来源合同。</li>
 * <li>参数：决策点格式非法或未支持、未知/重复/空 Query 与 GET Body 在处理前拒绝（400）。</li>
 *
 * @see RiskVariableApi#getAuthoringContext(String, String, String)
 */
class VariableAuthoringContextApiAutoTest extends AbstractVariableApiTest {
    private static final String PATH = "/decision-variables/v1/authoring-context";

    /** 不指定决策点时返回全部工程编排能力。 */
    @Test
    void test_normalFlow() {
        invoke(headers(), PATH).assertContext();
    }

    /** 指定已支持决策点时只返回该点的来源合同，本期即全部两个合同。 */
    @Test
    void test_normalFlow_supportedPoint() {
        invoke(headers(), PATH + "?pointCode=CHECKOUT_PREPARE").assertContext();
    }

    /** 查询串非法时在处理前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidQueries")
    void test_invalidParameter(String scenario, String query) {
        invoke(headers(), PATH + "?" + query).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 携带 Body 的 GET 请求。 */
    @Test
    void test_invalidParameter_getWithBody() {
        HttpHeaders headers = headers();

        new VariableAuthoringContextResponseChecker(http.exchange(HttpMethod.GET, PATH, headers, "{}"), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID)).assertProblem(400, INVALID_REQUEST.getCode());
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(Arguments.of("决策点小写", "pointCode=checkout_prepare"),
            Arguments.of("决策点未支持", "pointCode=PAYOUT_CREATION"),
            Arguments.of("决策点含非法字符", "pointCode=CHECKOUT-PREPARE"),
            Arguments.of("决策点超过 64 字符", "pointCode=" + "A".repeat(65)),
            Arguments.of("决策点为空值", "pointCode="),
            Arguments.of("决策点重复", "pointCode=CHECKOUT_PREPARE&pointCode=CHECKOUT_PREPARE"),
            Arguments.of("未知参数", "usage=RULE_CONDITION"));
    }

    private VariableAuthoringContextResponseChecker invoke(HttpHeaders headers, String uri) {
        return new VariableAuthoringContextResponseChecker(http.get(uri, headers), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }
}
