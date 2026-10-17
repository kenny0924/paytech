package com.paytech.acquiring.test.checker.order;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.acquiring.api.client.v1.enums.CancellationReason;
import com.paytech.acquiring.api.client.v1.enums.CancellationStatus;
import com.paytech.acquiring.api.client.v1.order.response.CancelOrderResponseBody;
import com.paytech.acquiring.domain.enums.CheckoutSessionStatus;
import com.paytech.acquiring.domain.enums.OrderStatus;
import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.databind.ObjectMapper;

/** 通过正式 ResponseBody 断言取消终态和待定响应，不维护字段名清单。 */
public class CancelOrderResponseChecker extends AbstractHttpResponseChecker<CancelOrderResponseChecker> {
    private final ObjectMapper mapper;

    /** 保留真实响应及当前请求标识。 */
    public CancelOrderResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
        this.mapper = mapper;
    }

    /** 保留公共断言的链式具体类型。 */
    @Override
    protected CancelOrderResponseChecker self() {
        return this;
    }

    /** 成功与待定均严格遵循 DTO 的序列化结构和可空字段规则。 */
    private CancelOrderResponseBody response() {
        assertContentType(MediaType.APPLICATION_JSON).assertRequestId();
        var value = mapper.readValue(result.getResponseBody(), CancelOrderResponseBody.class);
        assertEquals(mapper.valueToTree(value), body(), "取消响应结构应符合正式 DTO");
        return value;
    }

    /** 200 返回原取消时间，有实际 Session 才返回 Checkout 状态。 */
    public CancelOrderResponseChecker assertCancelled(String orderNo, boolean hasSession, Instant cancelledAt) {
        assertStatus(200).assertHeader(HttpHeaders.RETRY_AFTER, null);
        var value = response();
        assertAll(() -> assertEquals(orderNo, value.getOrderNo()),
            () -> assertEquals(OrderStatus.CANCELLED.getCode(), value.getOrderStatus()),
            () -> assertEquals(hasSession ? CheckoutSessionStatus.CANCELLED.getCode() : null, value.getCheckoutStatus()),
            () -> assertEquals(cancelledAt, value.getCancelledAt()),
            () -> assertNull(value.getCancellationStatus()), () -> assertNull(value.getReasonCode()));
        return this;
    }

    /** 202 只描述取消待定，不提前承诺订单已经关闭。 */
    public CancelOrderResponseChecker assertPending(String orderNo) {
        assertStatus(202).assertHeader(HttpHeaders.RETRY_AFTER, "3");
        var value = response();
        assertAll(() -> assertEquals(orderNo, value.getOrderNo()),
            () -> assertEquals(CancellationStatus.PENDING.getCode(), value.getCancellationStatus()),
            () -> assertEquals(CancellationReason.PAYMENT_IN_PROGRESS.getCode(), value.getReasonCode()),
            () -> assertNull(value.getOrderStatus()), () -> assertNull(value.getCheckoutStatus()),
            () -> assertNull(value.getCancelledAt()));
        return this;
    }
}
