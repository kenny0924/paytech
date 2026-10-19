package com.paytech.commercial.api.ratecard;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.commercial.api.ratecard.response.RateCardResponseBody;

import jakarta.validation.constraints.Pattern;

/** Client 费率卡只读接口（technique.md §2.7）；私网，调用方 client-console-bff。 */
@HttpExchange(url = "/rate-cards/v1", accept = "application/json")
public interface RateCardApi {
    /**
     * 读取 Client 的结构化费率卡
     *
     * @param clientId Client ID
     * @param asOf 费率卡时点（UTC RFC 3339），缺省为当前时间
     * @param currency 只返回该币种的价格块，可选
     * @return 200 与费率卡；没有 ACTIVE 合同时 agreements 为空列表
     */
    @GetExchange
    RateCardResponseBody getRateCard(
        @RequestParam("clientId") @Pattern(regexp = "[1-9][0-9]{0,18}") String clientId,
        @RequestParam(value = "asOf", required = false)
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,6})?Z") String asOf,
        @RequestParam(value = "currency", required = false) @Pattern(regexp = "[A-Z]{3}") String currency);
}
