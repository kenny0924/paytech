package com.paytech.acquiring.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.payment.api.execution.request.PayRequestBody;
import com.paytech.payment.api.execution.response.PayResponseBody;

/** Payment pay 的 HTTP Mock：只配置响应 DTO 与故障，调用记录不保存工具 Token、IP 或 User-Agent。 */
@TestComponent
public class CheckoutCardMocker implements Clear {
    private PayResponseBody response;
    private MockHttpFault failure;
    private Consumer<PayRequestBody> observation = request -> {};
    private final List<Call> calls = new ArrayList<>();

    /**
     * 一次 pay 调用的归属与付款人引用
     *
     * @param intentId 路径中的 Intent
     * @param idempotencyKey Idempotency-Key
     * @param clientId Client ID
     * @param sourceSubmissionId 来源提交 ID
     * @param captureMode 捕获模式
     * @param consumerReference 付款人引用
     * @param returnUrl 回跳地址
     */
    public record Call(String intentId, String idempotencyKey, String clientId, String sourceSubmissionId,
        String captureMode, String consumerReference, String returnUrl) {}

    /** 配置 pay 响应 DTO。 */
    public synchronized void mockResponse(PayResponseBody value) {
        response = value;
        failure = null;
    }

    /** 配置网络或下游故障。 */
    public synchronized void mockFailure(MockHttpFault value) {
        failure = value;
    }

    /** 在处理请求时断言字段，不把工具 Token 留在调用记录中。 */
    public synchronized void observe(Consumer<PayRequestBody> value) {
        observation = value;
    }

    /** 记录调用、执行观察后返回 DTO 或故障。 */
    public PayResponseBody answer(String intentId, String idempotencyKey, PayRequestBody body) {
        PayResponseBody result;
        MockHttpFault error;
        Consumer<PayRequestBody> observer;
        synchronized (this) {
            calls.add(new Call(intentId, idempotencyKey, body.getClientId(), body.getSourceSubmissionId(),
                body.getCaptureMode(), body.getConsumerReference(), body.getReturnUrl()));
            result = response;
            error = failure;
            observer = observation;
        }
        observer.accept(body);
        if (error != null) {
            throw error;
        }
        assertNotNull(result, "未配置 pay 响应 DTO");
        return result;
    }

    /** pay 调用记录。 */
    public synchronized List<Call> calls() {
        return List.copyOf(calls);
    }

    /** 处理中的 pay 响应。 */
    public static PayResponseBody processing(String attemptId) {
        var body = new PayResponseBody();
        body.setAttemptId(attemptId);
        body.setStatus("PROCESSING");
        return body;
    }

    @Override
    public synchronized void clear() {
        observation = request -> {};
        response = null;
        failure = null;
        calls.clear();
    }
}
