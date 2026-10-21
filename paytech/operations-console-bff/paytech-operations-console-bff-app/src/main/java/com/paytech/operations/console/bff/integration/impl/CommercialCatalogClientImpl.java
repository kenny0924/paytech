package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.CommercialContractConverter.request;
import static com.paytech.operations.console.bff.integration.impl.CommercialContractConverter.requireSame;
import static com.paytech.operations.console.bff.integration.impl.CommercialContractConverter.write;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.paytech.commercial.api.catalog.ProductCatalogApi;
import com.paytech.operations.console.bff.integration.CommercialCatalogClient;
import com.paytech.operations.console.bff.service.model.CommercialCatalogModels.CatalogCapabilityResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialCatalogModels.CatalogFeeEventResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialCatalogModels.CreateProductCapabilityRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialCatalogModels.ProductCatalogTransitionRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialCatalogModels.UpdateFeeEventCapabilityRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialCatalogModels.UpdateProductCapabilityRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import lombok.AllArgsConstructor;

/**
 * commercial 产品目录维护接口的 HTTP 转发：每个入口只调用一次 commercial，不重试
 * <li>命令把会话 Account ID 填入 X-Gateway-Actor-Subject；回执严格转换为目录行页面模型。</li>
 * <li>新建回执的可开通项代码与请求一致，改写与状态推进回执的可开通项 ID 与路径一致，计费事件回执的登记 ID 与路径一致。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class CommercialCatalogClientImpl implements CommercialCatalogClient {
    private final ProductCatalogApi catalogApi;

    /** 新增可开通项；201 与重放回执都取 Body。 */
    @Override
    public CatalogCapabilityResponseBody createCapability(OperatorContext operator, CreateProductCapabilityRequestBody request) {
        var capability = write(request, () -> {
            var response = catalogApi.createCapability(operator.accountId(),
                request(request, com.paytech.commercial.api.catalog.request.CreateProductCapabilityRequestBody.class));
            return response == null ? null : response.getBody();
        }, CatalogCapabilityResponseBody.class);
        requireSame(request.getCapabilityCode(), capability.getCapabilityCode());
        return capability;
    }

    /** 改写可开通项。 */
    @Override
    public CatalogCapabilityResponseBody updateCapability(OperatorContext operator, String productCapabilityId,
        UpdateProductCapabilityRequestBody request) {
        return sameCapability(productCapabilityId, request, () -> catalogApi.updateCapability(operator.accountId(),
            productCapabilityId, request(request,
                com.paytech.commercial.api.catalog.request.UpdateProductCapabilityRequestBody.class)));
    }

    /** 停用或启用可开通项。 */
    @Override
    public CatalogCapabilityResponseBody transitionCapability(OperatorContext operator, String productCapabilityId,
        CatalogAction action, ProductCatalogTransitionRequestBody request) {
        String actor = operator.accountId();
        var body = request(request, com.paytech.commercial.api.catalog.request.ProductCatalogTransitionRequestBody.class);
        return sameCapability(productCapabilityId, request, () -> switch (action) {
            case RETIRE -> catalogApi.retireCapability(actor, productCapabilityId, body);
            case ACTIVATE -> catalogApi.activateCapability(actor, productCapabilityId, body);
        });
    }

    /** 改写计费事件挂接；回执登记 ID 与路径一致。 */
    @Override
    public CatalogFeeEventResponseBody updateFeeEventCapability(OperatorContext operator, String productFeeEventId,
        UpdateFeeEventCapabilityRequestBody request) {
        var feeEvent = write(request, () -> catalogApi.updateFeeEventCapability(operator.accountId(), productFeeEventId,
            request(request, com.paytech.commercial.api.catalog.request.UpdateFeeEventCapabilityRequestBody.class)),
            CatalogFeeEventResponseBody.class);
        requireSame(productFeeEventId, feeEvent.getProductFeeEventId());
        return feeEvent;
    }

    private static CatalogCapabilityResponseBody sameCapability(String productCapabilityId, Object request, Supplier<Object> call) {
        var capability = write(request, call, CatalogCapabilityResponseBody.class);
        requireSame(productCapabilityId, capability.getProductCapabilityId());
        return capability;
    }
}
