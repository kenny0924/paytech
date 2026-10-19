package com.paytech.commercial.ext.rest;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.commercial.api.ratecard.RateCardApi;
import com.paytech.commercial.api.ratecard.response.RateCardResponseBody;
import com.paytech.commercial.domain.RateCardQuery;
import com.paytech.commercial.service.processor.RateCardQueryProcessor;

import lombok.AllArgsConstructor;

/** Client 费率卡的 HTTP 协议适配；时点缺省为当前时间。 */
@RestController
@AllArgsConstructor
public class RateCardController implements RateCardApi {
    private final Clock clock;
    private final RateCardQueryProcessor rateCardQueryProcessor;

    /** 读取费率卡。 */
    @Override
    public RateCardResponseBody getRateCard(String clientId, String asOf, String currency) {
        Instant time = asOf == null ? clock.instant().truncatedTo(ChronoUnit.MICROS) : Instant.parse(asOf);
        return RateCardConverter.toResponseBody(
            rateCardQueryProcessor.process(new RateCardQuery(Long.parseLong(clientId), time, currency)));
    }
}
