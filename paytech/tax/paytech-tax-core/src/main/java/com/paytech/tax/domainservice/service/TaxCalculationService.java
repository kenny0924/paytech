package com.paytech.tax.domainservice.service;

import com.paytech.tax.domain.calculation.CalculateTaxContext;

/** 税务计算的本地保存。 */
public interface TaxCalculationService {

    /**
     * 一个本地事务写入计算头、行、组件与证据
     *
     * @param context 已构造完整的新计算
     * @throws org.springframework.dao.DuplicateKeyException 同一计算身份已被并发保存（事务已回滚）
     */
    void saveCalculation(CalculateTaxContext context);
}
