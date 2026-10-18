package com.paytech.acquiring.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.PaymentStatus;
import com.paytech.acquiring.domainservice.port.customer.CheckoutPaymentPort;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.payment.api.execution.request.CreateIntentRequestBody;
import com.paytech.payment.api.execution.response.CreateIntentResponseBody;

/** Intent 创建通过真实 HTTP 返回提供方 DTO；暂未对接的概要查询仍使用测试端口。 */
@TestComponent
@Primary
public class CheckoutIntentMocker implements CheckoutPaymentPort, Clear {
    private CreateIntentResponseBody reply;
    private MockHttpFault failure;
    private Consumer<CreateIntentRequestBody> observation = request -> {};
    private final List<CreateIntentRequestBody> calls = new ArrayList<>();
    private PaymentStatus status;

    /** 配置正式创建响应。 */
    public synchronized void mockResult(CreateIntentResponseBody value) {
        reply = value;
        failure = null;
    }

    /** 配置真实 HTTP 故障，不替换生产 Client。 */
    public synchronized void mockFailure(MockHttpFault value) {
        failure = value;
    }

    /** 在远端响应前观察本地已提交事实。 */
    public synchronized void observe(Consumer<CreateIntentRequestBody> value) {
        observation = value;
    }

    /** 配置尚未对接的只读摘要。 */
    public synchronized void mockStatus(PaymentStatus value) {
        status = value;
    }

    /** 记录正式 DTO，执行场景观察后返回配置结果。 */
    public CreateIntentResponseBody answer(CreateIntentRequestBody request) {
        CreateIntentResponseBody result;
        MockHttpFault error;
        Consumer<CreateIntentRequestBody> observer;
        synchronized (this) {
            calls.add(request);
            result = reply;
            error = failure;
            observer = observation;
            assertTrue(result != null || error != null, "未配置 Intent 响应");
        }
        observer.accept(request);
        if (error != null) {
            throw error;
        }
        return result;
    }

    /** 查询只返回测试配置，不模拟 Payment 状态机。 */
    @Override
    public synchronized PaymentStatus query(long orderId, long intentId) {
        assertNotNull(status, "未配置支付摘要");
        return status;
    }

    /** 查看实际出站创建请求，不含卡或工具信息。 */
    public synchronized List<CreateIntentRequestBody> calls() {
        return List.copyOf(calls);
    }

    /** 每例清理响应、观察器与调用记录。 */
    @Override
    public synchronized void clear() {
        reply = null;
        failure = null;
        status = null;
        calls.clear();
        observation = request -> {};
    }
}
