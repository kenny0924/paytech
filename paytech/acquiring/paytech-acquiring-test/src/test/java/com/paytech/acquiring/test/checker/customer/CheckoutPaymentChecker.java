package com.paytech.acquiring.test.checker.customer;

import static org.junit.jupiter.api.Assertions.*;

import com.paytech.acquiring.test.dal.CheckoutPaymentTestMapper;

import lombok.AllArgsConstructor;

import org.springframework.boot.test.context.TestComponent;

/** 只读检查真实数据库中的唯一 Intent 与法律受理事实。 */
@TestComponent
@AllArgsConstructor
public class CheckoutPaymentChecker {
    private final CheckoutPaymentTestMapper mapper;

    public void assertUnstarted(long orderId) {
        assertNull(mapper.payment(orderId));
        assertEquals(0, mapper.acceptances(orderId));
        assertEquals(0, mapper.evidence(orderId));
    }

    public void assertAccepted(long orderId, boolean intentBound) {
        assertEquals(3, mapper.acceptances(orderId));
        assertEquals(1, mapper.evidence(orderId));
        var payment = mapper.payment(orderId);
        if (intentBound) {
            assertNotNull(payment);
            assertEquals(orderId, payment.getPaymentIntentId());
            assertEquals(0, payment.getIntentSourceVersion(), "创建响应不提供来源事件版本");
            assertEquals("USD", payment.getCurrency());
            assertEquals(1, payment.getVersion());
        } else {
            assertNull(payment);
        }
    }
}
