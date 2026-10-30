package com.paytech.tax.domainservice.service;

import com.paytech.tax.domain.calculation.TaxCalculationView;
import com.paytech.tax.domain.view.TaxOrderView;

/** 税务只读查询；普通展示读取，不开显式事务。 */
public interface TaxQueryService {

    /**
     * 按计算身份读取
     *
     * @return 计算全貌；不存在返回 null
     */
    TaxCalculationView findCalculation(String sourceSystem, String sourceObjectType, long sourceObjectId,
        int sourceRevision, String taxScopeKey);

    /**
     * 按 ID 读取计算
     *
     * @param calculationId 计算 ID
     * @return 计算全貌；不存在返回 null
     */
    TaxCalculationView findCalculation(long calculationId);

    /**
     * 订单号下的税务全貌
     *
     * @param orderNo 订单号（计算的 source_object_no）
     * @return 全貌；没有任何计算返回 null
     */
    TaxOrderView findOrderTax(String orderNo);
}
