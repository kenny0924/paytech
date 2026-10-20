package com.paytech.commercial.api.catalog;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.commercial.api.catalog.response.ProductCapabilityListResponseBody;
import com.paytech.commercial.api.catalog.response.ProductFeeEventListResponseBody;
import com.paytech.commercial.api.catalog.response.ProductListResponseBody;

/**
 * 产品目录只读接口（运营控制台第二切片 technique.md §2；仅私网，调用方 operations-console-bff）
 * <li>目录由初始化 DML 随产品发版维护，规模有限，不分页；只读，不加锁、不开事务。</li>
 */
@HttpExchange(url = "/products/v1", accept = "application/json")
public interface ProductCatalogApi {
    /**
     * 产品（L0）列表，按产品 ID 升序
     *
     * @param status 可选状态：DRAFT、ACTIVE、RETIRED
     * @return 产品列表；未知状态 400
     */
    @GetExchange
    ProductListResponseBody listProducts(@RequestParam(value = "status", required = false) String status);

    /**
     * 可开通项（L1）列表，按可开通项 ID 升序
     *
     * @param productCode 可选产品代码：ACQUIRING、PAYOUT、FX、DISPUTE、TAX_INVOICE、DATA_ANALYTICS、PLATFORM
     * @param status 可选状态：DRAFT、ACTIVE、RETIRED
     * @return 可开通项列表；未知代码 400
     */
    @GetExchange("/capabilities")
    ProductCapabilityListResponseBody listCapabilities(
        @RequestParam(value = "productCode", required = false) String productCode,
        @RequestParam(value = "status", required = false) String status);

    /**
     * 计费事件（L2）列表，按计费事件 ID 升序
     *
     * @param productCode 可选产品代码
     * @param termType 可选条款类型（开放目录代码，如 CLIENT_PRICING），用于价格编辑时只列出该条款类型登记的事件
     * @return 计费事件列表；未知产品代码 400
     */
    @GetExchange("/fee-events")
    ProductFeeEventListResponseBody listFeeEvents(
        @RequestParam(value = "productCode", required = false) String productCode,
        @RequestParam(value = "termType", required = false) String termType);
}
