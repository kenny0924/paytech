package com.paytech.operations.console.bff.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.CommercialPricePreviewClient;
import com.paytech.operations.console.bff.service.model.CommercialPricePreviewModels.CommercialPriceSimulationResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialPricePreviewModels.SimulateCommercialPriceRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialPrice;

import lombok.AllArgsConstructor;

/**
 * 运营价格试算：commercial 价格命中 → 可选的示例费用
 * <li>命中、父链与事实校验全部由 commercial 执行；命中失败（422 事实缺失 / 无命中 / 非法事实、409 歧义）原样透传其原因。</li>
 * <li>提供示例金额时由 CommercialFeeIllustrator 按命中价格计算示例费用，仅供运营核对价目，不是计费结果。</li>
 * <li>只读组合，不编排任何写入。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class CommercialPriceSimulationProcessor
    extends AbstractProcessor<SimulateCommercialPriceRequestBody, CommercialPriceSimulationResponseBody> {
    private final CommercialPricePreviewClient client;

    @Override
    protected CommercialPriceSimulationResponseBody processRequest(SimulateCommercialPriceRequestBody request) {
        CommercialPrice price = client.resolve(request);
        var result = new CommercialPriceSimulationResponseBody();
        result.setPrice(price);
        result.setIllustration(request.getAmount() == null ? null
            : CommercialFeeIllustrator.illustrate(price, request.getAmount()));
        return result;
    }
}
