package com.paytech.commerce.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.commerce.test.mocker.external.CardInstrumentMocker;
import com.paytech.payment.api.instrument.PaymentInstrumentApi;
import com.paytech.payment.api.instrument.request.ApplyCardInstrumentRequestBody;
import com.paytech.payment.api.instrument.response.ApplyCardInstrumentResponseBody;

import lombok.AllArgsConstructor;

/** 在独立 Mock 服务继承正式工具接口，由场景配置 DTO。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockPaymentInstrumentApi implements PaymentInstrumentApi {
    private final CardInstrumentMocker mocker;

    /** 专用入口只在本次调用内核对合成卡，不记录卡输入。 */
    @Override
    public ResponseEntity<ApplyCardInstrumentResponseBody> applyCardInstrument(ApplyCardInstrumentRequestBody body) {
        var request = (org.springframework.web.context.request.ServletRequestAttributes)
            org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes();
        request.getResponse().setHeader("X-Request-Id", "payment-test");
        return ResponseEntity.ok(mocker.answer(body));
    }
}
