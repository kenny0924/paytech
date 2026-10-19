package com.paytech.commercial.domainservice.repository.impl;

import static com.paytech.commercial.domainservice.repository.impl.PersistenceTimes.micros;

import java.util.Collection;
import java.util.List;

import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.core.dal.dataobject.AgreementProduct;
import com.paytech.commercial.core.dal.mapper.AgreementProductMapper;
import com.paytech.commercial.domainservice.repository.AgreementProductRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 AgreementProductMapper 访问 t_agreement_product。 */
@AllArgsConstructor
public class AgreementProductRepositoryImpl implements AgreementProductRepository {
    private final AgreementProductMapper mapper;

    /** 截断时间精度后插入并核对影响行数。 */
    @Override
    public void insert(AgreementProduct agreementProduct) {
        agreementProduct.setCreateTime(micros(agreementProduct.getCreateTime()));
        agreementProduct.setUpdateTime(micros(agreementProduct.getUpdateTime()));
        if (mapper.insert(agreementProduct) != 1) {
            throw new ErrorException("合同订阅插入行数不符: " + agreementProduct.getAgreementId() + ", 产品: "
                + agreementProduct.getProductCode());
        }
    }

    /** 按主键读取。 */
    @Override
    public AgreementProduct findById(long agreementProductId) {
        return mapper.selectById(agreementProductId);
    }

    /** 按唯一键读取。 */
    @Override
    public AgreementProduct findByAgreementAndProduct(long agreementId, ProductCode productCode) {
        return mapper.selectByAgreementAndProduct(agreementId, productCode);
    }

    /** 读取一份合同的全部订阅。 */
    @Override
    public List<AgreementProduct> findByAgreementId(long agreementId) {
        return List.copyOf(mapper.selectByAgreementId(agreementId));
    }

    /** 空集合不访问数据库。 */
    @Override
    public List<AgreementProduct> findByAgreementIds(Collection<Long> agreementIds) {
        if (agreementIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(mapper.selectByAgreementIds(agreementIds));
    }

    /** 行已被删除时视为并发变化。 */
    @Override
    public void delete(AgreementProduct agreementProduct) {
        if (mapper.deleteById(agreementProduct.getAgreementProductId()) != 1) {
            throw new ConcurrentRequestException("合同订阅已变化: " + agreementProduct.getAgreementId() + ", 产品: "
                + agreementProduct.getProductCode());
        }
    }
}
