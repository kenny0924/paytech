package com.paytech.tax.integration.fx;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 法定申报汇率端口（technique 9.4）
 * <li>本期不接法定汇率源，唯一实现恒返回空，底稿标 PENDING(FX_RATE_MISSING)；接入随申报需求。事务外调用。</li>
 */
public interface TaxFxRateClient {

    /**
     * 查询法定汇率
     *
     * @param sourceCurrency 交易币种
     * @param reportingCurrency 申报币种
     * @param rateDate 法定汇率日期
     * @return 汇率（source → reporting）；没有可用来源时为空
     */
    Optional<BigDecimal> findStatutoryRate(String sourceCurrency, String reportingCurrency, LocalDate rateDate);
}
