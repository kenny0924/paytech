package com.paytech.payment.ext.rest.instrument;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.payment.api.instrument.PaymentInstrumentApi;
import com.paytech.payment.api.instrument.request.ApplyCardInstrumentRequestBody;
import com.paytech.payment.api.instrument.response.ApplyCardInstrumentResponseBody;
import com.paytech.payment.execution.service.processor.ApplyCardInstrumentProcessor;
import com.paytech.payment.execution.service.processor.request.ApplyCardInstrumentRequest;
import com.paytech.payment.execution.service.processor.response.ApplyCardInstrumentResponse;
import com.paytech.payment.ext.rest.converter.ApplyCardInstrumentConverter;

import lombok.AllArgsConstructor;

/** 支付工具入口 */
@RestController
@AllArgsConstructor
public class PaymentInstrumentController implements PaymentInstrumentApi {
    final ApplyCardInstrumentConverter applyCardInstrumentConverter;
    final ApplyCardInstrumentProcessor applyCardInstrumentProcessor;

    /** 申请卡支付工具或返回原结果 */
    @Override
    public ResponseEntity<ApplyCardInstrumentResponseBody> applyCardInstrument(ApplyCardInstrumentRequestBody body) {
        ApplyCardInstrumentRequest request = applyCardInstrumentConverter.convertRequest(body);
        ApplyCardInstrumentResponse response = applyCardInstrumentProcessor.process(request);
        ApplyCardInstrumentResponseBody responseBody = applyCardInstrumentConverter.convertResponse(response);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responseBody);
    }
}
