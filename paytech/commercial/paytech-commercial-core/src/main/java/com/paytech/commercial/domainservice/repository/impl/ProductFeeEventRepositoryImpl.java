package com.paytech.commercial.domainservice.repository.impl;

import static com.paytech.commercial.domainservice.repository.impl.PersistenceTimes.micros;

import java.util.List;

import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.core.dal.dataobject.ProductFeeEvent;
import com.paytech.commercial.core.dal.mapper.ProductFeeEventMapper;
import com.paytech.commercial.domainservice.repository.ProductFeeEventRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 ProductFeeEventMapper 访问 t_product_fee_event。 */
@AllArgsConstructor
public class ProductFeeEventRepositoryImpl implements ProductFeeEventRepository {
    private final ProductFeeEventMapper mapper;

    /** 截断时间精度后插入并核对影响行数。 */
    @Override
    public void insert(ProductFeeEvent feeEvent) {
        feeEvent.setCreateTime(micros(feeEvent.getCreateTime()));
        feeEvent.setUpdateTime(micros(feeEvent.getUpdateTime()));
        if (mapper.insert(feeEvent) != 1) {
            throw new ErrorException(
                "计费事件目录插入行数不符: " + feeEvent.getFeeEventCode() + ", 条款类型: " + feeEvent.getTermType());
        }
    }

    /** 按主键读取。 */
    @Override
    public ProductFeeEvent findById(long productFeeEventId) {
        return mapper.selectById(productFeeEventId);
    }

    /** 按唯一键读取。 */
    @Override
    public ProductFeeEvent findByFeeEventAndTermType(String feeEventCode, String termType) {
        return mapper.selectByFeeEventAndTermType(feeEventCode, termType);
    }

    /** 读取某产品的全部计费事件。 */
    @Override
    public List<ProductFeeEvent> findByProductCode(ProductCode productCode) {
        return List.copyOf(mapper.selectByProductCode(productCode));
    }

    /** 读取某条款类型下的全部计费事件。 */
    @Override
    public List<ProductFeeEvent> findByTermType(String termType) {
        return List.copyOf(mapper.selectByTermType(termType));
    }

    /** 按可选条件读取计费事件。 */
    @Override
    public List<ProductFeeEvent> findFiltered(ProductCode productCode, String termType) {
        return List.copyOf(mapper.selectFiltered(productCode, termType));
    }
}
