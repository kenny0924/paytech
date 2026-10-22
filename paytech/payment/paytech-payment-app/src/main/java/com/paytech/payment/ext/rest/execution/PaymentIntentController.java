package com.paytech.payment.ext.rest.execution;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.payment.api.execution.PaymentIntentApi;
import com.paytech.payment.api.execution.request.CreateIntentRequestBody;
import com.paytech.payment.api.execution.request.PayRequestBody;
import com.paytech.payment.api.execution.request.RefreshSubmissionRequestBody;
import com.paytech.payment.api.execution.request.TerminateIntentRequestBody;
import com.paytech.payment.api.execution.response.CreateIntentResponseBody;
import com.paytech.payment.api.execution.response.GetIntentResponseBody;
import com.paytech.payment.api.execution.response.GetSubmissionResponseBody;
import com.paytech.payment.api.execution.response.PayResponseBody;
import com.paytech.payment.api.execution.response.TerminateIntentResponseBody;
import com.paytech.payment.execution.service.processor.CreateIntentProcessor;
import com.paytech.payment.execution.service.processor.GetIntentProcessor;
import com.paytech.payment.execution.service.processor.GetSubmissionProcessor;
import com.paytech.payment.execution.service.processor.PayProcessor;
import com.paytech.payment.execution.service.processor.RefreshSubmissionProcessor;
import com.paytech.payment.execution.service.processor.TerminateIntentProcessor;
import com.paytech.payment.execution.service.processor.request.CreateIntentRequest;
import com.paytech.payment.execution.service.processor.request.GetIntentRequest;
import com.paytech.payment.execution.service.processor.request.GetSubmissionRequest;
import com.paytech.payment.execution.service.processor.request.PayRequest;
import com.paytech.payment.execution.service.processor.request.TerminateIntentRequest;
import com.paytech.payment.execution.service.processor.response.CreateIntentResponse;
import com.paytech.payment.execution.service.processor.response.GetIntentResponse;
import com.paytech.payment.execution.service.processor.response.GetSubmissionResponse;
import com.paytech.payment.execution.service.processor.response.PayResponse;
import com.paytech.payment.execution.service.processor.response.TerminateIntentResponse;
import com.paytech.payment.ext.rest.converter.CreateIntentConverter;
import com.paytech.payment.ext.rest.converter.GetIntentConverter;
import com.paytech.payment.ext.rest.converter.GetSubmissionConverter;
import com.paytech.payment.ext.rest.converter.PayConverter;
import com.paytech.payment.ext.rest.converter.TerminateIntentConverter;

import lombok.AllArgsConstructor;

/** 支付受理入口 */
@RestController
@AllArgsConstructor
public class PaymentIntentController implements PaymentIntentApi {

    final PayConverter payConverter;
    final PayProcessor payProcessor;
    final GetIntentConverter getIntentConverter;
    final GetIntentProcessor getIntentProcessor;
    final GetSubmissionConverter getSubmissionConverter;
    final GetSubmissionProcessor getSubmissionProcessor;
    final RefreshSubmissionProcessor refreshSubmissionProcessor;
    final CreateIntentConverter createIntentConverter;
    final CreateIntentProcessor createIntentProcessor;
    final TerminateIntentConverter terminateIntentConverter;
    final TerminateIntentProcessor terminateIntentProcessor;

    /** 创建支付意图或返回原结果 */
    @Override
    public ResponseEntity<CreateIntentResponseBody> createIntent(CreateIntentRequestBody body) {
        CreateIntentRequest request = createIntentConverter.convertRequest(body);
        CreateIntentResponse response = createIntentProcessor.process(request);
        CreateIntentResponseBody responseBody = createIntentConverter.convertResponse(response);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responseBody);
    }

    /** 查询支付意图，正常结果统一返回 200。 */
    @Override
    public ResponseEntity<GetIntentResponseBody> getIntent(String intentId, String clientId) {
        GetIntentRequest request = getIntentConverter.convertRequest(intentId, clientId);
        GetIntentResponse response = getIntentProcessor.process(request);
        GetIntentResponseBody responseBody = getIntentConverter.convertResponse(response);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responseBody);
    }

    /** 按来源提交查询原支付结果，正常结果统一返回 200。 */
    @Override
    public ResponseEntity<GetSubmissionResponseBody> getSubmission(String intentId, String sourceSubmissionId,
        String clientId) {
        GetSubmissionRequest request = getSubmissionConverter.convertRequest(intentId, sourceSubmissionId, clientId);
        GetSubmissionResponse response = getSubmissionProcessor.process(request);
        GetSubmissionResponseBody responseBody = getSubmissionConverter.convertResponse(response);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responseBody);
    }

    /** 刷新来源提交的支付结果，按需触发一次查单确认，响应与 EX-I06 相同。 */
    @Override
    public ResponseEntity<GetSubmissionResponseBody> refreshSubmission(String intentId, String sourceSubmissionId,
        RefreshSubmissionRequestBody body) {
        GetSubmissionRequest request =
            getSubmissionConverter.convertRequest(intentId, sourceSubmissionId, body.getClientId());
        GetSubmissionResponse response = refreshSubmissionProcessor.process(request);
        GetSubmissionResponseBody responseBody = getSubmissionConverter.convertResponse(response);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responseBody);
    }

    /** 终止支付意图或返回当前未决结果。 */
    @Override
    public ResponseEntity<TerminateIntentResponseBody> terminateIntent(String intentId,
        TerminateIntentRequestBody body) {
        TerminateIntentRequest request = terminateIntentConverter.convertRequest(intentId, body);
        TerminateIntentResponse response = terminateIntentProcessor.process(request);
        TerminateIntentResponseBody responseBody = terminateIntentConverter.convertResponse(response);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(responseBody);
    }

    /** 受理支付提交或幂等重放 */
    @Override
    public ResponseEntity<PayResponseBody> pay(String intentId, String idempotencyKey, PayRequestBody body) {
        PayRequest request = payConverter.convertRequest(intentId, idempotencyKey, body);
        PayResponse response = payProcessor.process(request);
        PayResponseBody responseBody = payConverter.convertResponse(response);
        return ResponseEntity.status("PROCESSING".equals(responseBody.getStatus()) ? 202 : 200)
            .cacheControl(CacheControl.noStore()).body(responseBody);
    }
}
