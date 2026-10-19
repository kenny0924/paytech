package com.paytech.commercial.domainservice.repository.impl;

import static com.paytech.commercial.domainservice.repository.impl.PersistenceTimes.micros;

import java.time.Instant;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.core.dal.mapper.CommercialTermVersionMapper;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 CommercialTermVersionMapper 读取 t_commercial_term_version。 */
@AllArgsConstructor
public class CommercialTermVersionRepositoryImpl implements CommercialTermVersionRepository {
    private final CommercialTermVersionMapper mapper;

    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 commercial_term_version 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<CheckoutRecords.CommercialTermVersion> findForCheckout(CommercialCheckoutScope scope) {
        return List.copyOf(mapper.findForCheckout(scope.clientId(), scope.entityNos(), scope.localPartnerIds()));
    }

    /** 截断时间精度后插入并核对影响行数。 */
    @Override
    public void insert(CommercialTermVersion commercialTermVersion) {
        commercialTermVersion.setCreateTime(micros(commercialTermVersion.getCreateTime()));
        commercialTermVersion.setUpdateTime(micros(commercialTermVersion.getUpdateTime()));
        if (mapper.insert(commercialTermVersion) != 1) {
            throw new ErrorException("商业条款版本插入行数不符: " + commercialTermVersion.getTermVersionId());
        }
    }

    /** 按主键读取。 */
    @Override
    public CommercialTermVersion findById(long termVersionId) {
        return mapper.selectById(termVersionId);
    }

    /** 按唯一键（方案、版本号）读取。 */
    @Override
    public CommercialTermVersion findByPlanAndVersionNo(long termPlanId, long versionNo) {
        return mapper.selectByPlanAndVersionNo(termPlanId, versionNo);
    }

    /** 读取一个方案的全部版本，按版本号升序。 */
    @Override
    public List<CommercialTermVersion> findByPlanId(long termPlanId) {
        return List.copyOf(mapper.selectByPlanId(termPlanId));
    }

    /** 方案内当前最大版本号。 */
    @Override
    public Long findMaxVersionNo(long termPlanId) {
        return mapper.selectMaxVersionNo(termPlanId);
    }

    /** 按商业时点选取方案下唯一适用版本。 */
    @Override
    public CommercialTermVersion findEffectiveAt(long termPlanId, Instant asOf) {
        return mapper.findEffectiveAt(termPlanId, asOf);
    }

    /** 条件迁移，成功后同步原对象。 */
    @Override
    public void updateStatus(CommercialTermVersion version, CommercialTermVersionStatus status, Integer activeSlot,
        Instant now) {
        Instant time = micros(now);
        if (mapper.updateStatus(version, status, activeSlot, time) != 1) {
            throw new ConcurrentRequestException("商业条款版本状态已变化: " + version.getTermVersionId());
        }
        version.setStatus(status);
        version.setActiveSlot(activeSlot);
        version.setUpdateTime(time);
    }

    /** 条件改写，成功后同步原对象。 */
    @Override
    public void updateEffectivePeriod(CommercialTermVersion version, Instant effectiveFrom, Instant effectiveTo,
        Instant now) {
        Instant from = micros(effectiveFrom);
        Instant to = micros(effectiveTo);
        Instant time = micros(now);
        if (mapper.updateEffectivePeriod(version, from, to, time) != 1) {
            throw new ConcurrentRequestException("商业条款版本已变化: " + version.getTermVersionId());
        }
        version.setEffectiveFrom(from);
        version.setEffectiveTo(to);
        version.setUpdateTime(time);
    }
}
