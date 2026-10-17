package com.paytech.payment.api.instrument;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.payment.api.instrument.request.ApplyCardInstrumentRequestBody;
import com.paytech.payment.api.instrument.response.ApplyCardInstrumentResponseBody;

import jakarta.validation.Valid;

/** Payment 私有支付工具接口 */
@HttpExchange("/payment-instrument/v1")
public interface PaymentInstrumentApi {

    /**
     * 申请卡支付工具
     *
     * @param body
     *            请求
     * @return 响应
     *         <li>httpCode=200表示本次请求成功</li>
     */
    @PostExchange("/card-tokens")
    ResponseEntity<ApplyCardInstrumentResponseBody> applyCardInstrument(
        @Valid @RequestBody ApplyCardInstrumentRequestBody body);
}
