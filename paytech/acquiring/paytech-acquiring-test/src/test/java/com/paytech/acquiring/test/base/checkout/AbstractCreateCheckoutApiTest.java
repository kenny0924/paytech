package com.paytech.acquiring.test.base.checkout;

import java.time.Instant;

import com.paytech.acquiring.test.base.MockTestBase;
import com.paytech.acquiring.test.builder.external.CommercialFactsBuilder;
import com.paytech.acquiring.test.checker.checkout.CreateCheckoutResponseChecker;
import com.paytech.acquiring.test.mocker.external.CheckoutRiskMocker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

/** 创建接口共用的请求调用和固定场景基线，不包含测试方法或新增生命周期。 */
public abstract class AbstractCreateCheckoutApiTest extends MockTestBase {
    private static final String PATH = "/client-gateway-api/v1/checkouts";
    protected static final String CLIENT_ORDER_ID = "checkout-main";
    protected static final Instant NOW = Instant.parse("2026-09-08T00:00:00Z");

    /** 由各场景类的 BeforeEach 调用，每例重新配置本 Client 的 USD/US 预检 DTO 与 Risk 默认放行。 */
    protected final void mockDefaultPreparation() {
        preparationMocker.mockFacts(clientId(), "US", "USD", CommercialFactsBuilder.template().build());
        riskMocker.allow(CheckoutRiskMocker.DEFAULT_EVALUATION_ID);
    }

    /** 调用经过真实 Controller，不直接调用 Processor 或 Service 代替入口。 */
    protected final CreateCheckoutResponseChecker invoke(Object body) {
        return invoke(clientId(), body);
    }

    /** 使用主测试 Credential 调用真实 HTTP 入口。 */
    protected final CreateCheckoutResponseChecker invoke(long clientId, Object body) {
        return invoke(Long.toString(clientId), "1", body);
    }

    /** 允许省略 Header 以验证真实绑定边界，不为负例自动修复参数。 */
    protected final CreateCheckoutResponseChecker invoke(String clientId, String credentialId, Object body) {
        var headers = TestRequestHeaders.client(REQUEST_ID, clientId, credentialId);
        var result = httpTestInvoker.post(PATH, headers, body);
        return new CreateCheckoutResponseChecker(result, objectMapper, REQUEST_ID);
    }

    /** 当前运行隔离的主 Client。 */
    protected final long clientId() {
        return checkoutDataMocker.clientId();
    }

    /** 当前运行隔离的另一个 Client。 */
    protected final long otherClientId() {
        return checkoutDataMocker.otherClientId();
    }
}
