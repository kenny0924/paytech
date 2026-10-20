package com.paytech.counterparty.service.processor;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.counterparty.api.customer.ConsumerReferencePolicy;
import com.paytech.counterparty.domainservice.service.CustomerService;
import com.paytech.counterparty.service.processor.request.ClientCustomerRequest;
import com.paytech.counterparty.service.processor.response.ClientCustomerResponse;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** 创建 Customer；同一 Client 同一引用已有有效 Customer 时返回原 Customer。 */
@Component
@AllArgsConstructor
public class CreateCustomerProcessor extends AbstractProcessor<ClientCustomerRequest, ClientCustomerResponse> {
    private final Clock clock;
    private final CustomerService customerService;

    /** 拦截明显的个人原值；字符集与长度由 HTTP 入口校验。 */
    @Override
    protected void validate(ClientCustomerRequest request) {
        super.validate(request);
        ConsumerReferencePolicy.requireOpaque(request.getConsumerReference());
    }

    @Override
    protected ClientCustomerResponse processRequest(ClientCustomerRequest request) {
        var resolution = customerService.resolveActiveCustomer(request.getClientId(), request.getConsumerReference(),
            clock.instant().truncatedTo(ChronoUnit.MICROS));
        return new ClientCustomerResponse(resolution.customer(), resolution.created());
    }
}
