package com.paytech.operations.console.bff.integration;

import com.paytech.operations.console.bff.service.model.CommercialPricePreviewModels.RateCardResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricePreviewModels.SimulateCommercialPriceRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialPrice;

/**
 * commercial 费率卡与价格命中的本地端口（运营只读预览）；价格事实、命中算法与错误归 commercial
 * <li>两个入口都是只读查询，不携带操作人。</li>
 *
 * @author Claude
 */
public interface CommercialPricePreviewClient {

    /**
     * Client 的结构化费率卡
     *
     * @param clientId Client ID
     * @param asOf 可选时点（UTC RFC 3339），缺省为当前
     * @param currency 可选币种
     * @return 费率卡；回执 Client 与请求一致
     */
    RateCardResponseBody rateCard(String clientId, String asOf, String currency);

    /**
     * 在一个条款版本内按事实命中价格
     *
     * @param request 运营输入的事实
     * @return 命中价格；回执版本、OE、币种与请求一致
     */
    CommercialPrice resolve(SimulateCommercialPriceRequestBody request);
}
