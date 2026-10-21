package com.paytech.operations.console.bff.integration;

import com.paytech.operations.console.bff.service.model.CommercialCatalogModels.CatalogCapabilityResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialCatalogModels.CatalogFeeEventResponseBody;
import com.paytech.operations.console.bff.service.model.CommercialCatalogModels.CreateProductCapabilityRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialCatalogModels.ProductCatalogTransitionRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialCatalogModels.UpdateFeeEventCapabilityRequestBody;
import com.paytech.operations.console.bff.service.model.CommercialCatalogModels.UpdateProductCapabilityRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

/**
 * commercial 产品目录运营维护的本地端口；代码唯一、种类与支付方式、维度可用性、目录锁与版本冲突归 commercial
 * <li>命令以会话 Account ID 作为 X-Gateway-Actor-Subject 转发；不转发浏览器 Authorization、Cookie 或 Idempotency-Key。</li>
 *
 * @author Claude
 */
public interface CommercialCatalogClient {

    /**
     * 新增可开通项及其可配维度
     *
     * @param operator 操作人
     * @param request 可开通项内容
     * @return 新建的可开通项
     */
    CatalogCapabilityResponseBody createCapability(OperatorContext operator, CreateProductCapabilityRequestBody request);

    /**
     * 整体改写可开通项的名称、归属产品与可配维度
     *
     * @param operator 操作人
     * @param productCapabilityId 可开通项 ID
     * @param request 新内容与版本条件
     * @return 可开通项
     */
    CatalogCapabilityResponseBody updateCapability(OperatorContext operator, String productCapabilityId,
        UpdateProductCapabilityRequestBody request);

    /**
     * 可开通项停用或启用
     *
     * @param operator 操作人
     * @param productCapabilityId 可开通项 ID
     * @param action 停用或启用
     * @param request 版本条件
     * @return 可开通项
     */
    CatalogCapabilityResponseBody transitionCapability(OperatorContext operator, String productCapabilityId,
        CatalogAction action, ProductCatalogTransitionRequestBody request);

    /**
     * 改写计费事件挂接的开通项种类
     *
     * @param operator 操作人
     * @param productFeeEventId 计费事件登记 ID
     * @param request 挂接内容与版本条件
     * @return 计费事件
     */
    CatalogFeeEventResponseBody updateFeeEventCapability(OperatorContext operator, String productFeeEventId,
        UpdateFeeEventCapabilityRequestBody request);

    /** 可开通项状态推进动作。 */
    enum CatalogAction {
        /** ACTIVE / DRAFT → RETIRED */
        RETIRE,

        /** RETIRED / DRAFT → ACTIVE */
        ACTIVATE
    }
}
