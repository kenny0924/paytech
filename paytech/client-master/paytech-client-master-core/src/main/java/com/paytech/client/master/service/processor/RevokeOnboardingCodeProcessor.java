package com.paytech.client.master.service.processor;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.paytech.client.master.api.enums.OnboardingErrorCode;
import com.paytech.client.master.core.dal.dataobject.PartnerOnboardingCode;
import com.paytech.client.master.domain.enums.OnboardingCodeStatus;
import com.paytech.client.master.domainservice.repository.PartnerOnboardingCodeRepository;
import com.paytech.client.master.domainservice.service.OnboardingCodeService;
import com.paytech.client.master.service.processor.request.RevokeOnboardingCodeRequest;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * CM-12 作废入驻码：ACTIVE → REVOKED；已作废或已过期返回当前结果，不再改变
 * <li>与签发共用 client-master:local-partner:{id} 锁；条件更新保护并发的创建申请占用。</li>
 */
@AllArgsConstructor
public class RevokeOnboardingCodeProcessor extends AbstractLockProcessor<RevokeOnboardingCodeRequest, PartnerOnboardingCode> {
    private final Clock clock;
    private final Duration lockWait;
    private final Duration lockLease;
    private final OnboardingCodeService service;
    private final PartnerOnboardingCodeRepository codes;

    /** 与签发同一资源锁。 */
    @Override
    protected LockCommand lockCommand(RevokeOnboardingCodeRequest request) {
        return LockCommand.tryLocks(List.of("client-master:local-partner:" + request.getLocalPartnerId()), lockWait, lockLease);
    }

    /** 归属 → 状态 → 条件作废。 */
    @Override
    protected PartnerOnboardingCode processWithinLock(RevokeOnboardingCodeRequest request) {
        PartnerOnboardingCode code = codes.findById(request.getCodeId());
        if (code == null || code.getLocalPartnerId() != request.getLocalPartnerId()) {
            throw new FailException(OnboardingErrorCode.ONBOARDING_CODE_NOT_FOUND);
        }
        if (code.getStatus() != OnboardingCodeStatus.ACTIVE) {
            return code;
        }
        if (!service.revoke(code, request.getActorId(), clock.instant().truncatedTo(ChronoUnit.MICROS))) {
            throw new ConcurrentRequestException("入驻码状态已变化，请刷新后重试");
        }
        return code;
    }
}
