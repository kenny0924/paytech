package com.paytech.tax.app.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import com.paytech.foundation.web.api.ApiErrorStatusResolver;
import com.paytech.tax.api.enums.TaxErrorCode;

/** tax 合同错误码的 HTTP 状态：按 ID / 订单号读取不存在为 404，同一计算身份不同内容为 409；其余沿用共享处理器默认状态。 */
@Configuration(proxyBeanMethods = false)
public class TaxApiErrorConfiguration {

    /** TAX_CALCULATION_NOT_FOUND、TAX_ORDER_NOT_FOUND → 404；TAX_CALCULATION_CONFLICT → 409。 */
    @Bean
    ApiErrorStatusResolver taxErrorStatusResolver() {
        return (code, fallback) -> code instanceof TaxErrorCode tax ? switch (tax) {
            case TAX_CALCULATION_NOT_FOUND, TAX_ORDER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case TAX_CALCULATION_CONFLICT -> HttpStatus.CONFLICT;
        } : fallback;
    }
}
