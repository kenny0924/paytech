package com.paytech.acquiring.auto.ext.http.clientapi.v1.refund.create;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.UriComponentsBuilder;

import com.paytech.acquiring.api.client.v1.refund.ClientRefundApi;
import com.paytech.acquiring.api.client.v1.refund.request.CreateRefundRequestBody;
import com.paytech.acquiring.domain.enums.ClientWebhookEventType;
import com.paytech.acquiring.domain.enums.RefundStatus;
import com.paytech.acquiring.test.base.refund.AbstractCreateRefundApiTest;
import com.paytech.acquiring.test.checker.webhook.ClientWebhookChecker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 退款首次受理时冻结 refund.created：对象与 GET Refund 逐字段相同；同一 clientRefundId 重放不再登记或提交。
 *
 * @see ClientRefundApi#createRefund(long, long, String, String, CreateRefundRequestBody)
 */
class ClientWebhookRefundCreatedTest extends AbstractCreateRefundApiTest {
    @Autowired
    private ClientWebhookChecker webhooks;

    /** 已支付订单。 */
    @BeforeEach
    void setUpRefundCreatedWebhook() {
        fixture = orderData.template().paid().build();
        orderData.mock(fixture);
    }

    /**
     * 创建提交一次，对象等于创建时的 GET Refund 表示；createdAt 为退款写入时刻（秒精度）。
     * <li>提交后退款提交任务会把退款推进为 PROCESSING，GET 的状态、版本和更新时间按创建时的值比较。</li>
     */
    @Test
    void test_normalFlow() {
        var response = invoke(request()).assertAccepted(fixture.getOrder().getOrderNo(), "refund-main", "10.00", "USD",
            adjustableUtcClock.instant()).getResponseBody();
        String eventId = ClientWebhookEventType.REFUND_CREATED.eventId(response.getRefundRequestNo());

        var submission = webhooks.awaitSubmissions(eventId, 1);
        webhooks.awaitTaskCompleted(eventId);

        webhooks.assertFrozen(submission, ClientWebhookEventType.REFUND_CREATED, eventId,
            fixture.getOrder().getClientId(), "2026-09-08T00:00:00Z", refundAtCreation(response.getRefundRequestNo()));
        assertEquals(1L, submission.sourceVersion());
    }

    /** 同一 clientRefundId 重放返回原退款，不登记第二个事件。 */
    @Test
    void test_replayNotSubmitted() {
        var request = request();
        var response = invoke(request).assertAccepted(fixture.getOrder().getOrderNo(), "refund-main", "10.00", "USD",
            adjustableUtcClock.instant()).getResponseBody();
        String eventId = ClientWebhookEventType.REFUND_CREATED.eventId(response.getRefundRequestNo());
        webhooks.awaitSubmissions(eventId, 1);
        webhooks.awaitTaskCompleted(eventId);

        invoke(request).assertAccepted(fixture.getOrder().getOrderNo(), "refund-main", "10.00", "USD",
            adjustableUtcClock.instant());

        webhooks.assertNoTask(eventId);
        assertEquals(1, webhooks.submissions(eventId).size(), "重放不能再次提交");
    }

    /** GET Refund 表示，状态、版本与更新时间还原为创建时的 PENDING / 1 / createdAt；其余字段原样。 */
    JsonNode refundAtCreation(String refundRequestNo) {
        var refund = (ObjectNode)getRefund(refundRequestNo);
        refund.put("refundStatus", RefundStatus.PENDING.getCode());
        refund.put("version", 1);
        refund.set("updatedAt", refund.get("createdAt"));
        return refund;
    }

    /** 当前 GET Refund 表示。 */
    JsonNode getRefund(String refundRequestNo) {
        var path = UriComponentsBuilder.fromPath("/client-gateway-api/v1/refunds/{refundRequestNo}")
            .buildAndExpand(refundRequestNo).toUriString();
        var result = httpTestInvoker.get(path,
            TestRequestHeaders.client(REQUEST_ID, Long.toString(fixture.getOrder().getClientId()), "1"));
        assertEquals(200, result.getStatus().value());
        return objectMapper.readTree(result.getResponseBody());
    }
}
