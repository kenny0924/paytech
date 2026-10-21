package com.paytech.commercial.domainservice.repository.impl;

import static com.paytech.commercial.domainservice.repository.impl.PersistenceTimes.micros;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.commercial.api.enums.AgreementCapabilityStatus;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.mapper.AgreementCapabilityMapper;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 AgreementCapabilityMapper 访问 t_agreement_capability。 */
@AllArgsConstructor
public class AgreementCapabilityRepositoryImpl implements AgreementCapabilityRepository {
    private final AgreementCapabilityMapper mapper;

    /** 截断时间精度后插入并核对影响行数。 */
    @Override
    public void insert(AgreementCapability capability) {
        capability.setCreateTime(micros(capability.getCreateTime()));
        capability.setUpdateTime(micros(capability.getUpdateTime()));
        if (mapper.insert(capability) != 1) {
            throw new ErrorException("开通项插入行数不符: " + describe(capability));
        }
    }

    /** 按主键读取。 */
    @Override
    public AgreementCapability findById(long agreementCapabilityId) {
        return mapper.selectById(agreementCapabilityId);
    }

    /** 按唯一键读取。 */
    @Override
    public AgreementCapability findByUniqueKey(long agreementId, String entityNo, String capabilityCode) {
        return mapper.selectByUniqueKey(agreementId, entityNo, capabilityCode);
    }

    /** 读取一份合同的全部开通项。 */
    @Override
    public List<AgreementCapability> findByAgreementId(long agreementId) {
        return List.copyOf(mapper.selectByAgreementId(agreementId));
    }

    /** 空集合不访问数据库。 */
    @Override
    public List<AgreementCapability> findByAgreementIds(Collection<Long> agreementIds) {
        if (agreementIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(mapper.selectByAgreementIds(agreementIds));
    }

    /** 条件迁移，成功后同步原对象。 */
    @Override
    public void updateStatus(AgreementCapability capability, AgreementCapabilityStatus status, Instant now) {
        Instant time = micros(now);
        if (mapper.updateStatus(capability, status, time) != 1) {
            throw new ConcurrentRequestException("开通项状态已变化: " + describe(capability));
        }
        capability.setStatus(status);
        capability.setUpdateTime(time);
    }

    /** 以 update_time 为条件改写，成功后同步原对象。 */
    @Override
    public void updateTerms(AgreementCapability capability, Instant effectiveFrom, Instant effectiveTo,
        BigDecimal rollingReserveRate, Integer rollingReserveDays, Boolean require3ds, Instant now) {
        Instant from = micros(effectiveFrom);
        Instant to = micros(effectiveTo);
        Instant time = micros(now);
        if (mapper.updateTerms(capability, from, to, rollingReserveRate, rollingReserveDays, require3ds, time) != 1) {
            throw new ConcurrentRequestException("开通项已变化: " + describe(capability));
        }
        capability.setEffectiveFrom(from);
        capability.setEffectiveTo(to);
        capability.setRollingReserveRate(rollingReserveRate);
        capability.setRollingReserveDays(rollingReserveDays);
        capability.setRequire3ds(require3ds);
        capability.setUpdateTime(time);
    }

    /** 异常信息中的业务定位。 */
    private static String describe(AgreementCapability capability) {
        return capability.getAgreementCapabilityId() + ", 合同: " + capability.getAgreementId() + ", OE: "
            + capability.getEntityNo() + ", 可开通项: " + capability.getCapabilityCode();
    }
}
