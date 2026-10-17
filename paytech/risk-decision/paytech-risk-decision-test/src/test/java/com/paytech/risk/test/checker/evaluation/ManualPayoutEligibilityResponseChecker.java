package com.paytech.risk.test.checker.evaluation;

import static com.paytech.foundation.common.error.CommonErrorCode.SERVICE_UNAVAILABLE;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.databind.ObjectMapper;

/**
 * Manual Payout 资格评估 HTTP 响应 Checker
 * <li>协议、Request ID 与 Problem 断言复用 Foundation 基类；评估链接入前合法请求只会得到 Fail-Closed 的 503。</li>
 */
public final class ManualPayoutEligibilityResponseChecker
    extends AbstractHttpResponseChecker<ManualPayoutEligibilityResponseChecker> {

    /** 绑定单次 HTTP 结果及其 Request ID。 */
    public ManualPayoutEligibilityResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper,
        String requestId) {
        super(result, mapper, requestId);
    }

    /** 返回当前 Checker 以支持链式断言。 */
    @Override
    protected ManualPayoutEligibilityResponseChecker self() {
        return this;
    }

    /** 评估链尚未就绪：返回 503 且不泄露任何决定字段。 */
    public ManualPayoutEligibilityResponseChecker assertFailClosed() {
        assertProblem(503, SERVICE_UNAVAILABLE.getCode());
        org.junit.jupiter.api.Assertions.assertFalse(body().has("decision"), "Fail-Closed 响应不得包含风控决定");
        return this;
    }
}
