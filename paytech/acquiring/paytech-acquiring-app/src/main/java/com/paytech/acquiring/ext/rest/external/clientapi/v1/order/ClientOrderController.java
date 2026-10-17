package com.paytech.commerce.ext.rest.external.clientapi.v1.order;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.commerce.client.api.v1.order.ClientOrderApi;
import com.paytech.commerce.client.api.v1.order.response.CancelOrderResponseBody;
import com.paytech.commerce.client.api.v1.order.response.GetOrderResponseBody;
import com.paytech.commerce.service.processor.order.CancelOrderProcessor;
import com.paytech.commerce.service.processor.order.GetOrderProcessor;

import lombok.AllArgsConstructor;

/** 订单查询与取消接口的薄适配。 */
@RestController
@AllArgsConstructor
public class ClientOrderController implements ClientOrderApi {
    private final GetOrderProcessor getOrderProcessor;
    private final CancelOrderProcessor cancelOrderProcessor;

    @Override
    public GetOrderResponseBody getOrder(long clientId, long credentialId, String orderNo) {
        var request = GetOrderConverter.request(clientId, orderNo, null);
        var response = getOrderProcessor.process(request);
        return GetOrderConverter.response(response);
    }

    @Override
    public GetOrderResponseBody getOrderByClientOrderId(long clientId, long credentialId, String clientOrderId) {
        var request = GetOrderConverter.request(clientId, null, clientOrderId);
        var response = getOrderProcessor.process(request);
        return GetOrderConverter.response(response);
    }

    @Override
    public ResponseEntity<CancelOrderResponseBody> cancelOrder(long clientId, long credentialId, String orderNo) {
        var request = CancelOrderConverter.request(clientId, orderNo);
        var response = cancelOrderProcessor.process(request);
        var body = CancelOrderConverter.response(response);
        return response.isPending() ? ResponseEntity.accepted().header("Retry-After", "3").body(body)
            : ResponseEntity.ok(body);
    }
}
