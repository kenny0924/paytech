package com.paytech.commerce.test.mocker.external;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.commerce.integration.payment.PaymentTerminationClient;
import com.paytech.foundation.testsupport.api.Clear;

/** 暂缓 HTTP 集成时只配置终止端口 DTO / 异常，不模拟 Payment 状态机或创建 Intent。 */
@Primary
@TestComponent
public class PaymentTerminationMocker implements PaymentTerminationClient, Clear {
    private final Map<Long, Result> replies = new HashMap<>();
    private final Map<Long, RuntimeException> failures = new HashMap<>();
    private final List<Request> calls = new ArrayList<>();
    private final List<Boolean> transactions = new ArrayList<>();
    private boolean unexpectedCall;

    /** 每例按订单配置确定响应，允许构造错归属 DTO 等负例。 */
    public synchronized void mockResult(long orderId, Result result) {
        replies.put(orderId, result);
        failures.remove(orderId);
    }

    /** 故障只影响指定订单，不把未配置调用默认为成功。 */
    public synchronized void mockFailure(long orderId, RuntimeException failure) {
        failures.put(orderId, failure);
    }

    /** 记录真实 Processor 的调用及事务状态，返回本例配置的 DTO 或异常。 */
    @Override
    public synchronized Result terminate(Request request) {
        calls.add(request);
        transactions.add(TransactionSynchronizationManager.isActualTransactionActive());
        if (!replies.containsKey(request.orderId()) && !failures.containsKey(request.orderId())) {
            unexpectedCall = true;
            fail("未配置订单的 Payment 终止响应");
        }
        var failure = failures.get(request.orderId());
        if (failure != null) {
            throw failure;
        }
        return replies.get(request.orderId());
    }

    /** 返回本例调用记录供身份、次数及重提断言。 */
    public synchronized List<Request> calls() {
        return List.copyOf(calls);
    }

    /** 终止操作必须在本地事务之外。 */
    public synchronized void assertOutsideTransaction() {
        assertTrue(transactions.stream().noneMatch(Boolean::booleanValue), "事务内调用了 Payment 终止端口");
    }

    /** 本地取消、提前拒绝和终态重提不调用 Payment。 */
    public synchronized void assertNoCalls() {
        assertTrue(calls.isEmpty(), "不应调用 Payment 终止端口");
    }

    /** 后置清理所有响应及记录，并保留未配置调用的失败证据。 */
    @Override
    public synchronized void clear() {
        boolean unexpected = unexpectedCall;
        replies.clear();
        failures.clear();
        calls.clear();
        transactions.clear();
        unexpectedCall = false;
        assertFalse(unexpected, "存在未配置的 Payment 终止调用");
    }
}
