package com.paytech.acquiring.test.checker.refund;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.acquiring.api.client.v1.refund.response.ListOrderRefundsResponseBody;
import com.paytech.acquiring.api.client.v1.refund.response.ListOrderRefundsResponseBody.RefundInfo;
import com.paytech.acquiring.core.dal.dataobject.refund.RefundRequest;
import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.databind.ObjectMapper;

/** 业务字段通过正式响应对象断言，原始 JSON 只校验协议形状。 */
public final class ListOrderRefundsResponseChecker
    extends AbstractHttpResponseChecker<ListOrderRefundsResponseChecker> {
    private final ObjectMapper mapper;
    private ListOrderRefundsResponseBody responseBody;

    public ListOrderRefundsResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
        this.mapper = mapper;
    }

    @Override
    protected ListOrderRefundsResponseChecker self() {
        return this;
    }

    public ListOrderRefundsResponseBody getResponseBody() {
        if (responseBody == null) {
            responseBody = mapper.readValue(result.getResponseBody(), ListOrderRefundsResponseBody.class);
        }
        return responseBody;
    }

    /** DTO 定义响应结构；回序列化只检查协议一致性，业务预期由独立场景断言。 */
    public ListOrderRefundsResponseChecker assertSuccess() {
        assertStatus(200).assertContentType(MediaType.APPLICATION_JSON).assertRequestId();
        assertEquals(mapper.valueToTree(getResponseBody()), body(), "响应 JSON 应符合 ResponseBody 的序列化定义");
        assertNotNull(getResponseBody().getItems());
        return this;
    }

    /** 数量与顺序一起断言，避免空列表或重复项仍能通过。 */
    public ListOrderRefundsResponseChecker assertPage(boolean hasMore, String... refundNos) {
        assertSuccess();
        assertEquals(List.of(refundNos),
            getResponseBody().getItems().stream().map(RefundInfo::getRefundRequestNo).toList());
        if (hasMore) {
            assertNotNull(getResponseBody().getNextCursor());
            assertTrue(getResponseBody().getNextCursor().matches("r1\\.[A-Za-z0-9_-]+"));
        } else {
            assertNull(getResponseBody().getNextCursor());
        }
        return this;
    }

    /** 期望来自用例独立准备的 Fixture，金额格式由用例明确指定，不调用生产 Money 计算。 */
    public ListOrderRefundsResponseChecker assertRefund(RefundRequest expected, String orderNo, String amount) {
        var actual = getResponseBody().getItems().stream()
            .filter(item -> expected.getRefundRequestNo().equals(item.getRefundRequestNo())).findFirst().orElseThrow();
        assertAll(() -> assertEquals(expected.getClientRefundId(), actual.getClientRefundId()),
            () -> assertEquals(expected.getRequestOrigin().getCode(), actual.getRequestOrigin()),
            () -> assertEquals(orderNo, actual.getOrderNo()), () -> assertEquals(amount, actual.getAmount()),
            () -> assertEquals(expected.getCurrency(), actual.getCurrency()),
            () -> assertEquals(expected.getRefundStatus().getCode(), actual.getRefundStatus()),
            () -> assertEquals(expected.getReasonCode(), actual.getReasonCode()),
            () -> assertEquals(expected.getStatusReasonCode(), actual.getStatusReasonCode()),
            () -> assertEquals(expected.getCreateTime(), actual.getCreatedAt()),
            () -> assertEquals(expected.getCompletedAt(), actual.getCompletedAt()),
            () -> assertEquals(expected.getUpdateTime(), actual.getUpdatedAt()),
            () -> assertEquals(expected.getVersion(), actual.getVersion()));
        return this;
    }
}
