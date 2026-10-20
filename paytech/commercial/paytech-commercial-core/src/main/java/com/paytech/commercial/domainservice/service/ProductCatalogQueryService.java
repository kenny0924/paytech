package com.paytech.commercial.domainservice.service;

import java.util.List;

import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.domain.ProductCapabilityView;
import com.paytech.commercial.domain.ProductFeeEventView;
import com.paytech.commercial.domain.ProductView;

/** 产品目录（L0 产品、L1 可开通项、L2 计费事件）的只读查询；目录随初始化 DML 维护，规模有限，不分页、不开事务。 */
public interface ProductCatalogQueryService {
    /**
     * 产品列表
     *
     * @param status 状态，空不筛选
     * @return 按产品 ID 升序
     */
    List<ProductView> listProducts(ProductStatus status);

    /**
     * 可开通项列表
     *
     * @param productCode 产品代码，空不筛选
     * @param status 状态，空不筛选
     * @return 按可开通项 ID 升序
     */
    List<ProductCapabilityView> listCapabilities(ProductCode productCode, ProductStatus status);

    /**
     * 计费事件列表
     *
     * @param productCode 产品代码，空不筛选
     * @param termType 条款类型（开放目录代码），空不筛选
     * @return 按计费事件 ID 升序
     */
    List<ProductFeeEventView> listFeeEvents(ProductCode productCode, String termType);
}
