package com.paytech.client.master.domainservice.service.impl;

import java.time.Instant;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.master.core.dal.dataobject.PartnerOnboardingCode;
import com.paytech.client.master.domainservice.repository.PartnerOnboardingCodeRepository;
import com.paytech.client.master.domainservice.service.OnboardingCodeService;

import lombok.AllArgsConstructor;

/** 单表短事务；目录不进 BCR，操作者与时间记录在入驻码行本身。 */
@AllArgsConstructor
public class OnboardingCodeServiceImpl implements OnboardingCodeService {
    private final TransactionTemplate writes;
    private final PartnerOnboardingCodeRepository codes;

    /** 签发。 */
    @Override
    public void issue(PartnerOnboardingCode code) {
        writes.executeWithoutResult(status -> codes.insert(code));
    }

    /** 条件作废。 */
    @Override
    public boolean revoke(PartnerOnboardingCode code, long revokedBy, Instant now) {
        return Boolean.TRUE.equals(writes.execute(status -> codes.revoke(code, revokedBy, now)));
    }
}
