package com.paytech.commercial.domainservice.repository.impl;

import static com.paytech.commercial.domainservice.repository.impl.PersistenceTimes.micros;

import java.util.Collection;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;
import com.paytech.commercial.core.dal.mapper.AgreementCapabilityLimitMapper;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityLimitRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 AgreementCapabilityLimitMapper 访问 t_agreement_capability_limit。 */
@AllArgsConstructor
public class AgreementCapabilityLimitRepositoryImpl implements AgreementCapabilityLimitRepository {
    private final AgreementCapabilityLimitMapper mapper;

    /** 空列表不访问数据库；截断时间精度后批量插入并核对影响行数。 */
    @Override
    public void insertAll(List<AgreementCapabilityLimit> limits) {
        if (limits.isEmpty()) {
            return;
        }
        for (AgreementCapabilityLimit limit : limits) {
            limit.setCreateTime(micros(limit.getCreateTime()));
            limit.setUpdateTime(micros(limit.getUpdateTime()));
        }
        int inserted = mapper.insertAll(limits);
        if (inserted != limits.size()) {
            throw new ErrorException("开通项约束插入行数不符: 期望 " + limits.size() + ", 实际 " + inserted + ", 开通项: "
                + limits.get(0).getAgreementCapabilityId());
        }
    }

    /** 按主键读取。 */
    @Override
    public AgreementCapabilityLimit findById(long agreementCapabilityLimitId) {
        return mapper.selectById(agreementCapabilityLimitId);
    }

    /** 读取一个开通项的全部约束。 */
    @Override
    public List<AgreementCapabilityLimit> findByAgreementCapabilityId(long agreementCapabilityId) {
        return List.copyOf(mapper.selectByAgreementCapabilityId(agreementCapabilityId));
    }

    /** 空集合不访问数据库。 */
    @Override
    public List<AgreementCapabilityLimit> findByAgreementCapabilityIds(Collection<Long> agreementCapabilityIds) {
        if (agreementCapabilityIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(mapper.selectByAgreementCapabilityIds(agreementCapabilityIds));
    }

    /** 删除一个开通项的全部约束。 */
    @Override
    public int deleteByAgreementCapabilityId(long agreementCapabilityId) {
        return mapper.deleteByAgreementCapabilityId(agreementCapabilityId);
    }
}
