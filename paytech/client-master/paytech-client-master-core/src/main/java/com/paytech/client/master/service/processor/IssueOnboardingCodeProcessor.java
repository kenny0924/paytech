package com.paytech.client.master.service.processor;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;

import com.paytech.client.master.api.enums.EntityAdminErrorCode;
import com.paytech.client.master.api.enums.OnboardingErrorCode;
import com.paytech.client.master.api.enums.PartnerRoleCode;
import com.paytech.client.master.core.dal.dataobject.PartnerOnboardingCode;
import com.paytech.client.master.domain.enums.LocalPartnerStatus;
import com.paytech.client.master.domain.enums.OnboardingCodeStatus;
import com.paytech.client.master.domainservice.repository.LocalPartnerRepository;
import com.paytech.client.master.domainservice.repository.PartnerRoleRepository;
import com.paytech.client.master.domainservice.service.OnboardingCodeService;
import com.paytech.client.master.service.processor.request.IssueOnboardingCodeRequest;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * CM-12 签发 PayFac 入驻码（Q2 / Q4）：只有 ACTIVE 且具备 ACTIVE OE_PROVIDER 角色的 Local Partner 可签发
 * <li>与 Partner 角色维护共用 client-master:local-partner:{id} 锁，避免签发与撤销 OE_PROVIDER 交错。</li>
 * <li>码为 32 字符集（去掉易混淆的 I、O、0、1）随机 8 位，格式 XXXX-XXXX；唯一键碰撞时重新生成，最多 3 次。</li>
 */
@AllArgsConstructor
public class IssueOnboardingCodeProcessor extends AbstractLockProcessor<IssueOnboardingCodeRequest, PartnerOnboardingCode> {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Clock clock;
    private final IdGenerator ids;
    private final Duration lockWait;
    private final Duration lockLease;
    private final OnboardingCodeService codes;
    private final PartnerRoleRepository partnerRoles;
    private final LocalPartnerRepository localPartners;

    /** 有效期顺序只依赖输入。 */
    @Override
    protected void validate(IssueOnboardingCodeRequest request) {
        if (request.getEffectiveFrom() != null && request.getEffectiveTo() != null
            && !request.getEffectiveTo().isAfter(request.getEffectiveFrom())) {
            throw new InvalidParameterException("effectiveTo 须晚于 effectiveFrom");
        }
    }

    /** 与 Partner 角色维护同一资源锁。 */
    @Override
    protected LockCommand lockCommand(IssueOnboardingCodeRequest request) {
        return LockCommand.tryLocks(List.of("client-master:local-partner:" + request.getLocalPartnerId()), lockWait, lockLease);
    }

    /** Partner 资格 → 生成并保存。 */
    @Override
    protected PartnerOnboardingCode processWithinLock(IssueOnboardingCodeRequest request) {
        var partner = localPartners.findById(request.getLocalPartnerId());
        if (partner == null) {
            throw new FailException(EntityAdminErrorCode.LOCAL_PARTNER_NOT_FOUND);
        }
        boolean provider = partnerRoles.findActiveByPartners(List.of(request.getLocalPartnerId())).stream()
            .anyMatch(role -> role.getRoleCode() == PartnerRoleCode.OE_PROVIDER);
        if (partner.status() != LocalPartnerStatus.ACTIVE || !provider) {
            throw new FailException(OnboardingErrorCode.PARTNER_NOT_OE_PROVIDER);
        }
        for (int attempt = 0; ; attempt++) {
            PartnerOnboardingCode code = code(request);
            try {
                codes.issue(code);
                return code;
            } catch (DuplicateKeyException collision) {
                if (attempt >= 2) {
                    throw collision;
                }
            }
        }
    }

    /** 新入驻码。 */
    PartnerOnboardingCode code(IssueOnboardingCodeRequest request) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var code = new PartnerOnboardingCode();
        code.setCodeId(ids.nextId());
        code.setCode(random(4) + "-" + random(4));
        code.setLocalPartnerId(request.getLocalPartnerId());
        code.setStatus(OnboardingCodeStatus.ACTIVE);
        code.setEffectiveFrom(request.getEffectiveFrom() == null ? now : request.getEffectiveFrom().truncatedTo(ChronoUnit.MICROS));
        code.setEffectiveTo(request.getEffectiveTo() == null ? null : request.getEffectiveTo().truncatedTo(ChronoUnit.MICROS));
        code.setMaxUses(request.getMaxUses());
        code.setUseCount(0);
        code.setNote(request.getNote());
        code.setIssuedBy(request.getActorId());
        code.setCreateTime(now);
        code.setUpdateTime(now);
        return code;
    }

    /** 安全随机字符。 */
    static String random(int length) {
        var builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }
}
