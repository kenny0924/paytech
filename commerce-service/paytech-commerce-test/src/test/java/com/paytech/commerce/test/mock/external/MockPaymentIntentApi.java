package com.paytech.commerce.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.paytech.commerce.test.mocker.external.CheckoutCardMocker;
import com.paytech.commerce.test.mocker.external.CheckoutIntentMocker;
import com.paytech.payment.api.card.StoredCardApi;
import com.paytech.payment.api.execution.PaymentIntentApi;
import com.paytech.payment.api.execution.request.*;
import com.paytech.payment.api.execution.response.*;
import com.paytech.payment.api.instrument.PaymentInstrumentApi;

import lombok.AllArgsConstructor;

/** 独立 Mock 服务直接实现 Payment 合同，不复制接口方法与路径。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockPaymentIntentApi implements PaymentIntentApi, WebMvcConfigurer {
    private final CheckoutIntentMocker mocker;
    private final CheckoutCardMocker cards;

    /** 模拟提供方应用的 Context Path，接口内路径仍由正式 API 定义。 */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/payment", type -> PaymentIntentApi.class.isAssignableFrom(type)
            || PaymentInstrumentApi.class.isAssignableFrom(type) || StoredCardApi.class.isAssignableFrom(type));
    }

    /** 仅创建接口在本次范围内，通过 DTO 配置响应。 */
    @Override
    public ResponseEntity<CreateIntentResponseBody> createIntent(CreateIntentRequestBody body) {
        var request = (org.springframework.web.context.request.ServletRequestAttributes)
            org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes();
        request.getResponse().setHeader("X-Request-Id", "payment-test");
        return ResponseEntity.ok(mocker.answer(body));
    }

    /** 不能以本次测试伪造已完成 Intent 查询接入。 */
    @Override
    public ResponseEntity<GetIntentResponseBody> getIntent(String intentId, String clientId) {
        throw new AssertionError("本例不允许调用 getIntent");
    }

    /** 终止沿用独立测试边界，不在本次伪造 HTTP 接入。 */
    @Override
    public ResponseEntity<TerminateIntentResponseBody> terminateIntent(String intentId, TerminateIntentRequestBody body) {
        throw new AssertionError("本例不允许调用 terminateIntent");
    }

    /** pay 由场景配置 DTO 或故障；HTTP 状态按 Payment 合同，处理中返回 202。 */
    @Override
    public ResponseEntity<PayResponseBody> pay(String intentId, String idempotencyKey, PayRequestBody body) {
        var request = (org.springframework.web.context.request.ServletRequestAttributes)
            org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes();
        request.getResponse().setHeader("X-Request-Id", "payment-test");
        var response = cards.answer(intentId, idempotencyKey, body);
        return "PROCESSING".equals(response.getStatus()) ? ResponseEntity.accepted().body(response)
            : ResponseEntity.ok(response);
    }
}
