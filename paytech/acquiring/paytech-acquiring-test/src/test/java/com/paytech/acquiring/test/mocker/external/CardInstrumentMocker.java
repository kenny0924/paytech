package com.paytech.commerce.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.payment.api.instrument.request.ApplyCardInstrumentRequestBody;
import com.paytech.payment.api.instrument.response.ApplyCardInstrumentResponseBody;

/** 工具 Mock 只配置 DTO 与故障；调用记录不保留 PAN、CVV 或工具 Token。 */
@TestComponent
public class CardInstrumentMocker implements Clear {
    private ApplyCardInstrumentResponseBody response;
    private MockHttpFault fault;
    private Consumer<ApplyCardInstrumentRequestBody> observation = request -> {};
    private final List<Call> calls = new ArrayList<>();

    /** 最小调用归属，不保存敏感请求。 */
    public record Call(String clientId, String intentId, String sourceSubmissionId, String paymentMethodCode) {}

    /** 配置外部接口的原始响应 DTO。 */
    public synchronized void mockResponse(ApplyCardInstrumentResponseBody value) {
        response = value;
        fault = null;
    }

    /** 配置网络或协议故障。 */
    public synchronized void mockFailure(MockHttpFault value) {
        fault = value;
    }

    /** 在处理合成请求时断言字段，不把卡原值留在调用记录中。 */
    public synchronized void observe(Consumer<ApplyCardInstrumentRequestBody> value) {
        observation = value;
    }

    /** 执行观察后发送正式 DTO 或已配置故障。 */
    public ApplyCardInstrumentResponseBody answer(ApplyCardInstrumentRequestBody request) {
        ApplyCardInstrumentResponseBody result;
        MockHttpFault error;
        Consumer<ApplyCardInstrumentRequestBody> observer;
        synchronized (this) {
            calls.add(new Call(request.getClientId(), request.getIntentId(), request.getSourceSubmissionId(), request.getPaymentMethodCode()));
            result = response;
            error = fault;
            observer = observation;
        }
        observer.accept(request);
        if (error != null) {
            throw error;
        }
        assertNotNull(result, "未配置卡工具响应");
        return result;
    }

    /** 返回不包含卡原值的调用记录。 */
    public synchronized List<Call> calls() {
        return List.copyOf(calls);
    }

    /** 每例清除响应 Token、观察器、故障和调用记录。 */
    @Override
    public synchronized void clear() {
        response = null;
        fault = null;
        observation = request -> {};
        calls.clear();
    }
}
