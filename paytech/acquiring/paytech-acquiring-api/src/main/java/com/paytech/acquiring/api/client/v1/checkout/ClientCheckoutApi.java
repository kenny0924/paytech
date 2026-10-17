package com.paytech.commerce.client.api.v1.checkout;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.commerce.client.api.v1.checkout.request.CreateCheckoutRequestBody;
import com.paytech.commerce.client.api.v1.checkout.response.CreateCheckoutResponseBody;
import com.paytech.commerce.client.api.v1.common.ClientApiPaths;
import com.paytech.foundation.common.http.ApiHeaderNames;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Client Server 使用的 Hosted Checkout API。 */
@HttpExchange(value = ClientApiPaths.V1, accept = MediaType.APPLICATION_JSON_VALUE)
public interface ClientCheckoutApi {

    /**
     * 创建 Hosted Checkout 及其 Order。
     *
     * @param clientId
     *            Gateway 注入的 Client ID
     * @param credentialId
     *            Gateway 注入的 Credential ID
     * @param requestBody
     *            Checkout 请求
     * @return {@code 201 Created} 与不可变创建回执
     */
    @PostExchange(value = "/checkouts", contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CreateCheckoutResponseBody> createCheckout(
        @RequestHeader(ApiHeaderNames.GATEWAY_CLIENT_ID) @Positive long clientId,
        @RequestHeader(ApiHeaderNames.GATEWAY_CREDENTIAL_ID) @Positive long credentialId,
        @RequestBody @NotNull @Valid CreateCheckoutRequestBody requestBody);

}
