package com.paytech.commerce.test.mocker.external;

import static org.junit.jupiter.api.Assertions.*;

import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.*;
import com.paytech.commerce.domainservice.port.customer.CheckoutCardPaymentPort;
import com.paytech.foundation.testsupport.api.Clear;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

/** 返回配置的 DTO 内容，只回显当前请求归属，不保存卡字段或模拟扣款状态机。 */
@TestComponent
@Primary
public class CheckoutCardMocker implements CheckoutCardPaymentPort, Clear {
    private PaymentResult response;
    private RuntimeException failure;
    private java.util.function.Consumer<PaymentRequest> observation = request -> {};
    private final List<PaymentReference> calls = new ArrayList<>();
    private final List<Boolean> transactions = new ArrayList<>();

    public synchronized void mockResponse(PaymentResult value) {
        response = value;
        failure = null;
    }

    public synchronized void mockFailure(RuntimeException value) {
        failure = value;
    }

    /** 在调用时检查工具传递，不保存 Token 或原始输入。 */
    public synchronized void observe(java.util.function.Consumer<PaymentRequest> value) {
        observation = value;
    }

    @Override
    public synchronized PaymentResult submit(PaymentRequest request) {
        observation.accept(request);
        var ref = request.receipt().reference();
        calls.add(ref);
        transactions.add(TransactionSynchronizationManager.isActualTransactionActive());
        if (failure != null) {
            throw failure;
        }
        assertNotNull(response, "未配置支付提交 DTO");
        return new PaymentResult(
                ref.submissionId(),
                ref.orderId(),
                ref.intentId(),
                response.attemptId(),
                response.status(),
                response.last4(),
                response.nextAction(),
                response.failureCode(),
                response.observedAt());
    }

    @Override
    public PaymentResult query(PaymentReference ref) {
        throw new AssertionError("本例未配置支付查询");
    }

    public synchronized List<PaymentReference> calls() {
        return List.copyOf(calls);
    }

    public synchronized List<Boolean> transactions() {
        return List.copyOf(transactions);
    }

    @Override
    public synchronized void clear() {
        observation = request -> {};
        response = null;
        failure = null;
        calls.clear();
        transactions.clear();
    }
}
