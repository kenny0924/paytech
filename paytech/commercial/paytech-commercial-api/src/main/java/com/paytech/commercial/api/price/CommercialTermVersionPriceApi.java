package com.paytech.commercial.api.price;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.commercial.api.price.response.CommercialTermVersionPricesResponseBody;

import jakarta.validation.constraints.Pattern;

/** 固定版本下的价格全集（stage3 新增 5）；私网，调用方 Payment / Finance / operations-console-bff。 */
@HttpExchange(url = "/commercial-term-versions/v1", accept = "application/json")
public interface CommercialTermVersionPriceApi {
    /**
     * 读取版本下某 OE 的全部价格、画像条件、组件与档位
     *
     * @param versionId 版本 ID
     * @param entityNo 准确 OE，必填
     * @param termType 条款类型，必填，必须等于版本所属方案的条款类型
     * @param currency 币种，可选
     * @return 200 与价格全集（最多 1000 条）；版本不存在或条款类型不符 404
     */
    @GetExchange("/{versionId}/prices")
    CommercialTermVersionPricesResponseBody listPrices(
        @PathVariable("versionId") @Pattern(regexp = "[1-9][0-9]{0,18}") String versionId,
        @RequestParam("entityNo") @Pattern(regexp = "[A-Z]{2}[0-9]{3}") String entityNo,
        @RequestParam("termType") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String termType,
        @RequestParam(value = "currency", required = false) @Pattern(regexp = "[A-Z]{3}") String currency);
}
