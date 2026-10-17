package com.paytech.arch.fixture.r7.violation.ledgercore;

import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/fx/v1")
public interface ExchangeRateClient {

    @GetExchange("/rates")
    String rates();
}
