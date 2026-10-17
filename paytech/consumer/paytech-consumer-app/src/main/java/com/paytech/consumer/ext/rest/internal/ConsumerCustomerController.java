package com.paytech.consumer.ext.rest.internal;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.consumer.api.customer.ConsumerCustomerApi;
import com.paytech.consumer.api.customer.request.ResolveCustomerRequestBody;
import com.paytech.consumer.api.customer.response.CardSavingEligibilityResponseBody;
import com.paytech.consumer.api.customer.response.CustomerSummaryResponseBody;
import com.paytech.consumer.api.customer.response.ResolveCustomerResponseBody;
import com.paytech.consumer.service.processor.CreateCustomerProcessor;
import com.paytech.consumer.service.processor.GetCardSavingEligibilityProcessor;
import com.paytech.consumer.service.processor.GetCustomerByIdProcessor;
import com.paytech.consumer.service.processor.request.CardSavingEligibilityRequest;
import com.paytech.consumer.service.processor.request.ClientCustomerRequest;
import com.paytech.consumer.service.processor.request.CustomerIdRequest;

import lombok.AllArgsConstructor;

/** acquiring 使用的 consumer 内部接口薄适配（私网 HTTP，不经 Gateway）。 */
@RestController
@AllArgsConstructor
public class ConsumerCustomerController implements ConsumerCustomerApi {
    private final CreateCustomerProcessor createCustomerProcessor;
    private final GetCustomerByIdProcessor getCustomerByIdProcessor;
    private final GetCardSavingEligibilityProcessor cardSavingEligibilityProcessor;

    /** 与 Client API 创建 Customer 相同的建或取语义。 */
    @Override
    public ResolveCustomerResponseBody resolveCustomer(ResolveCustomerRequestBody requestBody) {
        var response = createCustomerProcessor.process(new ClientCustomerRequest(Long.parseLong(requestBody.getClientId()),
            null, requestBody.getConsumerReference(), null));
        var customer = response.customer();
        return new ResolveCustomerResponseBody(Long.toString(customer.getCustomerId()), customer.getCustomerNo(),
            customer.getCustomerStatus().getCode(), response.created());
    }

    @Override
    public CustomerSummaryResponseBody getCustomer(String customerId) {
        var customer = getCustomerByIdProcessor.process(new CustomerIdRequest(Long.parseLong(customerId)));
        return new CustomerSummaryResponseBody(Long.toString(customer.getCustomerId()), customer.getCustomerNo(),
            Long.toString(customer.getClientId()), customer.getCustomerStatus().getCode());
    }

    @Override
    public CardSavingEligibilityResponseBody cardSavingEligibility(String clientId, String customerId) {
        var response = cardSavingEligibilityProcessor
            .process(new CardSavingEligibilityRequest(Long.parseLong(clientId), Long.parseLong(customerId)));
        return new CardSavingEligibilityResponseBody(response.isEligible(),
            response.reason() == null ? null : response.reason().getCode(), response.customerNo());
    }
}
