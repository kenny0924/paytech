package com.paytech.commercial.api.price;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.commercial.api.price.request.ResolveCommercialPriceRequestBody;
import com.paytech.commercial.api.price.response.CommercialPriceResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 交易后价格命中与不可变价格回读（stage3 §2.3，technique.md §2.4）；私网，调用方 billing
 * <li>命中顺序：币种精确 → 支付方式精确 → 条件等值 → 不同维度数多者先 → priorityNo 小者先。</li>
 */
@HttpExchange(url = "/commercial-prices/v1", accept = "application/json")
public interface CommercialPriceApi {
    /**
     * 在冻结版本内命中唯一价格
     *
     * @param body 版本父链、商业时点、币种与受控支付事实
     * @return 200 与命中价格；422 PRICING_FACTS_INCOMPLETE / PRICE_NOT_FOUND / INVALID_PRICING_FACT；409 PRICE_AMBIGUOUS
     *         （同具体度同优先级或父链冲突）；版本不存在 404
     */
    @PostExchange("/resolutions")
    CommercialPriceResponseBody resolve(@Valid @RequestBody ResolveCommercialPriceRequestBody body);

    /**
     * 按价格 ID 回读同一不可变价格对象，不重新选价
     *
     * @param priceId 价格 ID
     * @return 200 与价格；不存在 404
     */
    @GetExchange("/{priceId}")
    CommercialPriceResponseBody getPrice(
        @PathVariable("priceId") @Pattern(regexp = "[1-9][0-9]{0,18}") String priceId);
}
