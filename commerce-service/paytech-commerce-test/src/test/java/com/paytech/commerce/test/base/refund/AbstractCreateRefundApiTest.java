package com.paytech.commerce.test.base.refund;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.util.UriComponentsBuilder;

import com.paytech.commerce.client.api.v1.refund.request.CreateRefundRequestBody;
import com.paytech.commerce.test.base.MockTestBase;
import com.paytech.commerce.test.builder.order.GetOrderDataBuilder.Fixture;
import com.paytech.commerce.test.builder.refund.CreateRefundRequestBuilder;
import com.paytech.commerce.test.checker.refund.CreateRefundResponseChecker;
import com.paytech.commerce.test.checker.refund.RefundCreationChecker;
import com.paytech.commerce.test.mocker.local.OrderQueryDataMocker;
import com.paytech.commerce.test.mocker.local.RefundCreationDataMocker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

/** 两组 HTTP 场景共用正式请求、真实调用器和数据组件；不配置内部成功替身。 */
public abstract class AbstractCreateRefundApiTest extends MockTestBase {
    protected static final String REQUEST_ID = "Commerce.CreateRefund.Test";
    @Autowired
    protected OrderQueryDataMocker orderData;
    @Autowired
    protected RefundCreationDataMocker refundData;
    @Autowired
    protected RefundCreationChecker refundChecker;
    protected Fixture fixture;

    protected CreateRefundRequestBody request() {
        return CreateRefundRequestBuilder.template().build();
    }

    protected CreateRefundResponseChecker invoke(CreateRefundRequestBody body) {
        return invoke(Long.toString(fixture.getOrder().getClientId()), "1", fixture.getOrder().getOrderNo(),
            "gateway-handles-this-key", body);
    }

    /** 不修复负例 Header、路径或 JSON，Gateway Key 只按现有 HTTP 契约传递。 */
    protected CreateRefundResponseChecker invoke(String client, String credential, String orderNo, String key,
        Object body) {
        var headers = TestRequestHeaders.client(REQUEST_ID, client, credential);
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        var path = UriComponentsBuilder.fromPath("/client-gateway-api/v1/orders/{orderNo}/refunds")
            .buildAndExpand(orderNo).toUriString();
        var before = refundChecker.unchangedFacts(fixture.getOrder().getOrderId());
        var result = httpTestInvoker.post(path, headers, body);
        assertEquals(before, refundChecker.unchangedFacts(fixture.getOrder().getOrderId()));
        cctCheckoutMocker.assertNoCalls();
        return new CreateRefundResponseChecker(result, objectMapper, REQUEST_ID);
    }
}
