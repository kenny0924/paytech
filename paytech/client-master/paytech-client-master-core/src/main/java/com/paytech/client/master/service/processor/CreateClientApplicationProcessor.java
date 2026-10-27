package com.paytech.client.master.service.processor;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.INVALID_DRAFT;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.contract.api.onboarding.ClientDraftModels.CommandReceiptResponseBody;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.StartApplicationRequestBody;
import com.paytech.client.master.api.enums.OnboardingErrorCode;
import com.paytech.client.master.api.enums.PartnerRoleCode;
import com.paytech.client.master.core.dal.dataobject.Jurisdiction;
import com.paytech.client.master.core.dal.dataobject.OnboardingSchema;
import com.paytech.client.master.core.dal.dataobject.PartnerOnboardingCode;
import com.paytech.client.master.domain.enums.AcceptancePolicy;
import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.enums.LocalPartnerStatus;
import com.paytech.client.master.domain.enums.OnboardingCodeStatus;
import com.paytech.client.master.domain.enums.RuleCode;
import com.paytech.client.master.domain.onboarding.ClientApplicationTerms;
import com.paytech.client.master.domain.onboarding.ClientDraftCommand;
import com.paytech.client.master.domain.onboarding.pack.OnboardingPack;
import com.paytech.client.master.domain.onboarding.pack.RuleSettings;
import com.paytech.client.master.domainservice.repository.LocalPartnerRepository;
import com.paytech.client.master.domainservice.repository.OrganizationTypeRepository;
import com.paytech.client.master.domainservice.repository.PartnerOnboardingCodeRepository;
import com.paytech.client.master.domainservice.repository.PartnerRoleRepository;
import com.paytech.client.master.domainservice.service.ClientDraftService;
import com.paytech.client.master.domainservice.service.OnboardingPackService;
import com.paytech.client.master.integration.ClientDraftAccountClient;
import com.paytech.client.master.service.processor.request.CreateClientApplicationRequest;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * C02 开始新申请（CM-3）：为账号分配唯一的未完成申请名额，按辖区包冻结 Schema，按入驻码冻结责任 PayFac。
 * <li>按账号名额 client-onboarding:account-slot:{accountId} 串行；Platform 会话在事务外核验。</li>
 * <li>辖区须 ACTIVE 且准入策略不是 CLOSED、Schema 可解析，否则 JURISDICTION_NOT_ACCEPTED；入驻码不可用一律 ONBOARDING_CODE_INVALID。</li>
 * <li>账号已有未完成申请时返回原申请（RESTORED），不占用入驻码；新建与重新申请在同一事务占用入驻码一次。</li>
 */
@AllArgsConstructor
public class CreateClientApplicationProcessor
    extends AbstractLockProcessor<CreateClientApplicationRequest, CommandReceiptResponseBody> {
    private static final String CODE_PATTERN = "[A-Z0-9]{4}-[A-Z0-9]{4}";

    private final Clock clock;
    private final Duration lockWait;
    private final Duration lockLease;
    private final ClientDraftService service;
    private final OnboardingPackService packs;
    private final ClientDraftAccountClient accounts;
    private final PartnerRoleRepository partnerRoles;
    private final LocalPartnerRepository localPartners;
    private final OrganizationTypeRepository organizationTypes;
    private final PartnerOnboardingCodeRepository onboardingCodes;
    private final TransactionTemplate writes;

    /** 与原注解锁 client-onboarding:account-slot 生成同一逻辑 Key。 */
    @Override
    protected LockCommand lockCommand(CreateClientApplicationRequest request) {
        return LockCommand.tryLocks(List.of(ClientOnboardingLocks.accountSlot(request.getAccountId())), lockWait,
            lockLease);
    }

    /** 会话 → 申请条款与入驻码（事务外只读）→ 本地事务创建或恢复。 */
    @Override
    protected CommandReceiptResponseBody processWithinLock(CreateClientApplicationRequest request) {
        accounts.authorize(request.getAccountId(), request.getSessionId());
        StartApplicationRequestBody body = request.getBody();
        ClientType type = clientType(body);
        PartnerOnboardingCode code = onboardingCode(body == null ? null : body.onboardingCode());
        ClientApplicationTerms terms = terms(body, type, code);
        var command = ClientDraftCommand.of(request.getAccountId(), "CLIENT_APPLICATION_CREATE");
        return ClientOnboardingCommands.retryActiveSlotCollision(
            () -> writes.execute(tx -> service.create(command, type, terms, code)));
    }

    /** 申请类型只接受闭合目录内的 Code；空值或目录外取值以 clientType 字段错误拒绝。 */
    static ClientType clientType(StartApplicationRequestBody request) {
        String code = request == null ? null : request.clientType();
        for (ClientType type : ClientType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new RequestBodyValidationException(INVALID_DRAFT,
            List.of(new ProblemFieldError("clientType", "须为 INDIVIDUAL 或 ORGANIZATION")), null);
    }

    /**
     * 辖区准入、组织类型适用性与 Schema 解析
     *
     * @return 冻结条款
     */
    ClientApplicationTerms terms(StartApplicationRequestBody body, ClientType type, PartnerOnboardingCode code) {
        String jurisdictionCode = body.jurisdictionCode() == null ? null : body.jurisdictionCode().strip();
        Jurisdiction jurisdiction = packs.jurisdiction(jurisdictionCode);
        if (jurisdiction == null || !OnboardingPack.active(jurisdiction.getStatus())) {
            throw new FailException(OnboardingErrorCode.JURISDICTION_NOT_ACCEPTED);
        }
        RuleSettings rules = packs.rules(jurisdictionCode);
        AcceptancePolicy policy = AcceptancePolicy.fromCode(rules.value(RuleCode.ACCEPTANCE_POLICY, jurisdictionCode));
        if (policy == null || policy == AcceptancePolicy.CLOSED) {
            throw new FailException(OnboardingErrorCode.JURISDICTION_NOT_ACCEPTED);
        }
        String organizationType = organizationType(body.organizationTypeCode(), jurisdictionCode, type, rules);
        OnboardingSchema schema = packs.resolveSchema(jurisdictionCode, type, organizationType);
        if (schema == null) {
            throw new FailException(OnboardingErrorCode.JURISDICTION_NOT_ACCEPTED);
        }
        return new ClientApplicationTerms(jurisdictionCode, organizationType, schema.getSchemaId(),
            code == null ? null : code.getCodeId(), code == null ? null : code.getLocalPartnerId());
    }

    /** 组织类型须为本辖区 ACTIVE 目录项，且 PARTY_TYPE 规则与申请主体类型一致；未填为空。 */
    String organizationType(String raw, String jurisdictionCode, ClientType type, RuleSettings rules) {
        String code = raw == null || raw.isBlank() ? null : raw.strip();
        if (code == null) {
            return null;
        }
        boolean valid = organizationTypes.findByJurisdiction(jurisdictionCode).stream()
            .anyMatch(row -> row.getTypeCode().equals(code) && OnboardingPack.active(row.getStatus())
                && type.getCode().equals(rules.value(RuleCode.PARTY_TYPE, jurisdictionCode + ":" + code)));
        if (!valid) {
            throw new RequestBodyValidationException(INVALID_DRAFT,
                List.of(new ProblemFieldError("organizationTypeCode", "当前辖区不支持此组织类型")), null);
        }
        return code;
    }

    /**
     * 入驻码：规范化为大写后须存在、ACTIVE、在有效期、未用尽，且签发 Partner 仍为 ACTIVE 并具备 ACTIVE OE_PROVIDER 角色
     * <li>任一不满足都只返回 ONBOARDING_CODE_INVALID，不泄露 Partner 或原因。</li>
     */
    PartnerOnboardingCode onboardingCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.strip().toUpperCase(Locale.ROOT);
        PartnerOnboardingCode code = normalized.matches(CODE_PATTERN) ? onboardingCodes.findByCode(normalized) : null;
        Instant now = clock.instant();
        if (code == null || code.getStatus() != OnboardingCodeStatus.ACTIVE || code.getEffectiveFrom().isAfter(now)
            || (code.getEffectiveTo() != null && !code.getEffectiveTo().isAfter(now))
            || (code.getMaxUses() != null && code.getUseCount() >= code.getMaxUses()) || !oeProvider(code.getLocalPartnerId())) {
            throw new FailException(OnboardingErrorCode.ONBOARDING_CODE_INVALID);
        }
        return code;
    }

    /** Partner 为 ACTIVE 且具备 ACTIVE OE_PROVIDER 角色。 */
    boolean oeProvider(long localPartnerId) {
        var partner = localPartners.findById(localPartnerId);
        return partner != null && partner.status() == LocalPartnerStatus.ACTIVE
            && partnerRoles.findActiveByPartners(List.of(localPartnerId)).stream()
                .anyMatch(role -> role.getRoleCode() == PartnerRoleCode.OE_PROVIDER);
    }
}
