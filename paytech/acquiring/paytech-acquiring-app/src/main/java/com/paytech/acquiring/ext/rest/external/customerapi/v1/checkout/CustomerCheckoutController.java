package com.paytech.commerce.ext.rest.external.customerapi.v1.checkout;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.commerce.customer.api.v1.checkout.CustomerCheckoutApi;
import com.paytech.commerce.customer.api.v1.checkout.request.SaveConsumerRequestBody;
import com.paytech.commerce.customer.api.v1.checkout.response.GetCheckoutResponseBody;
import com.paytech.commerce.customer.api.v1.checkout.response.SaveConsumerResponseBody;
import com.paytech.commerce.service.processor.customer.GetCustomerCheckoutProcessor;
import com.paytech.commerce.service.processor.customer.SaveConsumerProcessor;

import lombok.AllArgsConstructor;

/** Customer 订单查询与消费者资料接口；支付由同服务 CustomerPaymentController 提供。 */
@RestController
@AllArgsConstructor
public class CustomerCheckoutController implements CustomerCheckoutApi {
    private final SaveConsumerProcessor saveConsumerProcessor;
    private final GetCustomerCheckoutProcessor getCustomerCheckoutProcessor;

    /** 查询只投影当前授权的有限字段。 */
    @Override
    public GetCheckoutResponseBody getCheckout(String token, boolean includeConsumer) {
        var request = GetCheckoutConverter.request(token, includeConsumer);
        var response = getCustomerCheckoutProcessor.process(request);
        return GetCheckoutConverter.response(response);
    }

    /** 成功只返回保存状态，不回显个人原值。 */
    @Override
    public SaveConsumerResponseBody saveConsumer(String token, SaveConsumerRequestBody body) {
        var request = SaveConsumerConverter.request(token, body);
        var response = saveConsumerProcessor.process(request);
        return SaveConsumerConverter.response(response);
    }
}
