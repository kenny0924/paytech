package com.paytech.counterparty.ext.rest.internal;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.counterparty.api.customer.CounterpartyCustomerApi;
import com.paytech.counterparty.api.customer.request.ResolveCustomerRequestBody;
import com.paytech.counterparty.api.customer.response.CardSavingEligibilityResponseBody;
import com.paytech.counterparty.api.customer.response.CustomerSummaryResponseBody;
import com.paytech.counterparty.api.customer.response.ResolveCustomerResponseBody;
import com.paytech.counterparty.service.processor.CreateCustomerProcessor;
import com.paytech.counterparty.service.processor.GetCardSavingEligibilityProcessor;
import com.paytech.counterparty.service.processor.GetCustomerByIdProcessor;
import com.paytech.counterparty.service.processor.request.CardSavingEligibilityRequest;
import com.paytech.counterparty.service.processor.request.ClientCustomerRequest;
import com.paytech.counterparty.service.processor.request.CustomerIdRequest;

import lombok.AllArgsConstructor;

/** acquiring 使用的 counterparty 内部接口薄适配（私网 HTTP，不经 Gateway）。 */
@RestController
@AllArgsConstructor
public class CounterpartyCustomerController implements CounterpartyCustomerApi {
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
