package com.paytech.commercial.domainservice.repository.impl;

import static com.paytech.commercial.domainservice.repository.impl.PersistenceTimes.micros;

import java.util.Collection;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.AgreementOperatingEntity;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.mapper.AgreementOperatingEntityMapper;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domainservice.repository.AgreementOperatingEntityRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 AgreementOperatingEntityMapper 读取 t_agreement_operating_entity。 */
@AllArgsConstructor
public class AgreementOperatingEntityRepositoryImpl implements AgreementOperatingEntityRepository {
    private final AgreementOperatingEntityMapper mapper;

    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 agreement_operating_entity 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<CheckoutRecords.AgreementOperatingEntity> findForCheckout(CommercialCheckoutScope scope) {
        return List.copyOf(mapper.findForCheckout(scope.clientId(), scope.entityNos(), scope.localPartnerIds()));
    }

    /** 空列表不访问数据库；截断时间精度后批量插入并核对影响行数。 */
    @Override
    public void insertAll(List<AgreementOperatingEntity> rows) {
        if (rows.isEmpty()) {
            return;
        }
        for (AgreementOperatingEntity row : rows) {
            row.setCreateTime(micros(row.getCreateTime()));
            row.setUpdateTime(micros(row.getUpdateTime()));
        }
        int inserted = mapper.insertAll(rows);
        if (inserted != rows.size()) {
            throw new ErrorException("合同覆盖 OE插入行数不符: 期望 " + rows.size() + ", 实际 " + inserted);
        }
    }

    /** 按主键读取。 */
    @Override
    public AgreementOperatingEntity findById(long agreementOperatingEntityId) {
        return mapper.selectById(agreementOperatingEntityId);
    }

    /** 按唯一键（合同、OE）读取。 */
    @Override
    public AgreementOperatingEntity findByUniqueKey(long agreementId, String entityNo) {
        return mapper.selectByUniqueKey(agreementId, entityNo);
    }

    /** 读取一份合同覆盖的全部 OE。 */
    @Override
    public List<AgreementOperatingEntity> findByAgreementId(long agreementId) {
        return List.copyOf(mapper.selectByAgreementId(agreementId));
    }

    /** 空集合不访问数据库。 */
    @Override
    public List<AgreementOperatingEntity> findByAgreementIds(Collection<Long> agreementIds) {
        if (agreementIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(mapper.selectByAgreementIds(agreementIds));
    }

    /** 行已被删除时视为并发变化。 */
    @Override
    public void delete(AgreementOperatingEntity agreementOperatingEntity) {
        if (mapper.deleteById(agreementOperatingEntity.getAgreementOperatingEntityId()) != 1) {
            throw new ConcurrentRequestException("覆盖关系已变化: " + agreementOperatingEntity.getAgreementOperatingEntityId());
        }
    }
}
