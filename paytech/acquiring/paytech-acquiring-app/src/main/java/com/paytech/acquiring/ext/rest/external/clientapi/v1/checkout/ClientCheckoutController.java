package com.paytech.commerce.ext.rest.external.clientapi.v1.checkout;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.commerce.client.api.v1.checkout.ClientCheckoutApi;
import com.paytech.commerce.client.api.v1.checkout.request.CreateCheckoutRequestBody;
import com.paytech.commerce.client.api.v1.checkout.response.CreateCheckoutResponseBody;
import com.paytech.commerce.service.processor.checkout.CreateCheckoutProcessor;

import lombok.AllArgsConstructor;

/** Commerce Client Server Hosted Checkout 的窄 HTTP 入站适配器。 */
@RestController
@AllArgsConstructor
public class ClientCheckoutController implements ClientCheckoutApi {

    private final CreateCheckoutProcessor createCheckoutProcessor;

    /** 创建入口只负责协议转换及 HTTP 201 回执。 */
    @Override
    public ResponseEntity<CreateCheckoutResponseBody> createCheckout(long clientId, long credentialId,
        CreateCheckoutRequestBody requestBody) {
        var request = CreateCheckoutConverter.toRequest(clientId, requestBody);
        var response = createCheckoutProcessor.process(request);
        return ResponseEntity.created(CreateCheckoutConverter.toLocation(response))
            .body(CreateCheckoutConverter.toResponse(response));
    }

}
