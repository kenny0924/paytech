package com.paytech.tax.integration.fx.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.paytech.tax.integration.fx.TaxFxRateClient;

/**
 * 法定汇率尚未接入（technique 9.4、D-01）：一律返回空，不伪造汇率
 * <li>跨币种底稿因此保持 PENDING(FX_RATE_MISSING)；交易币种口径的计算与入账不受影响。</li>
 */
@Component
public class UnavailableTaxFxRateClient implements TaxFxRateClient {

    /** 没有法定汇率来源。 */
    @Override
    public Optional<BigDecimal> findStatutoryRate(String sourceCurrency, String reportingCurrency, LocalDate rateDate) {
        return Optional.empty();
    }
}
