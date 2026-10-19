package com.paytech.commercial.domainservice.repository.impl;

import static com.paytech.commercial.domainservice.repository.impl.PersistenceTimes.micros;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.commercial.api.enums.PricingProfileKind;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.dataobject.PricingProfile;
import com.paytech.commercial.core.dal.mapper.PricingProfileMapper;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domain.enums.PricingProfileStatus;
import com.paytech.commercial.domainservice.repository.PricingProfileRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 PricingProfileMapper 读取 t_pricing_profile。 */
@AllArgsConstructor
public class PricingProfileRepositoryImpl implements PricingProfileRepository {
    private final PricingProfileMapper mapper;

    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 pricing_profile 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<CheckoutRecords.PricingProfile> findForCheckout(CommercialCheckoutScope scope) {
        return List.copyOf(mapper.findForCheckout(scope.clientId(), scope.entityNos(), scope.localPartnerIds()));
    }

    /** 截断时间精度后插入并核对影响行数。 */
    @Override
    public void insert(PricingProfile pricingProfile) {
        pricingProfile.setCreateTime(micros(pricingProfile.getCreateTime()));
        pricingProfile.setUpdateTime(micros(pricingProfile.getUpdateTime()));
        if (mapper.insert(pricingProfile) != 1) {
            throw new ErrorException("定价画像插入行数不符: " + pricingProfile.getPricingProfileId());
        }
    }

    /** 按主键读取。 */
    @Override
    public PricingProfile findById(long pricingProfileId) {
        return mapper.selectById(pricingProfileId);
    }

    /** 按画像代码（uk_pricing_profile_code）读取。 */
    @Override
    public PricingProfile findByCode(String pricingProfileCode) {
        return mapper.selectByCode(pricingProfileCode);
    }

    /** 空集合不访问数据库。 */
    @Override
    public List<PricingProfile> findByCodes(Collection<String> pricingProfileCodes) {
        if (pricingProfileCodes.isEmpty()) {
            return List.of();
        }
        return List.copyOf(mapper.selectByCodes(pricingProfileCodes));
    }

    /** 按种类与状态筛选。 */
    @Override
    public List<PricingProfile> findByKindAndStatus(PricingProfileKind profileKind, PricingProfileStatus status) {
        return List.copyOf(mapper.selectByKindAndStatus(profileKind, status));
    }

    /** 条件迁移，成功后同步原对象。 */
    @Override
    public void updateStatus(PricingProfile pricingProfile, PricingProfileStatus status, Instant now) {
        Instant time = micros(now);
        if (mapper.updateStatus(pricingProfile, status, time) != 1) {
            throw new ConcurrentRequestException("画像状态已变化: " + pricingProfile.getPricingProfileId());
        }
        pricingProfile.setStatus(status);
        pricingProfile.setUpdateTime(time);
    }

    /** 条件改写，成功后同步原对象。 */
    @Override
    public void updateDraft(PricingProfile profile, String profileName, PricingProfileKind profileKind, String paymentMethodCode,
        Instant now) {
        Instant time = micros(now);
        if (mapper.updateDraft(profile, profileName, profileKind, paymentMethodCode, time) != 1) {
            throw new ConcurrentRequestException("定价画像已变化: " + profile.getPricingProfileCode());
        }
        profile.setProfileName(profileName);
        profile.setProfileKind(profileKind);
        profile.setPaymentMethodCode(paymentMethodCode);
        profile.setUpdateTime(time);
    }
}
