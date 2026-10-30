package com.paytech.payout.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryActorType;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.api.enums.PayoutErrorCode;
import com.paytech.payout.core.configuration.BeneficiaryRequirementProperties.RouteRequirement;
import com.paytech.payout.core.constant.PayoutLocks;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.domain.BeneficiaryAccountDetails;
import com.paytech.payout.domain.BeneficiaryAccountView;
import com.paytech.payout.domain.BeneficiaryContents;
import com.paytech.payout.domainservice.repository.BeneficiaryAccountRepository;
import com.paytech.payout.domainservice.service.BeneficiaryAccountService;
import com.paytech.payout.domainservice.validator.BeneficiaryFieldValidator;
import com.paytech.payout.integration.BeneficiaryVaultClient;
import com.paytech.payout.service.processor.request.SubmitBeneficiaryAccountRequest;
import com.paytech.payout.service.processor.response.SubmitBeneficiaryAccountResponse;

import lombok.AllArgsConstructor;

/**
 * 提交新收款账户（P1，R4、B1、B2、B6、B7、B8）：字段校验 → 事务外 Vault Protect ×2 → 查重 → PENDING_REVIEW
 * <li>锁 PAYOUT_BENEFICIARY_OWNER：同 Owner 的编号分配与查重串行；uk_beneficiary_owner_token 兜底。</li>
 * <li>原值 Protect 的幂等键由 Owner、路由与原值派生，同内容重放得到同一 Token 与摘要：同 Owner 同账号已有未终结账户时，摘要相同返回原账户
 * （AC-10），不同则 409 BENEFICIARY_DUPLICATE。不同 Owner 共用账号允许提交，由运营视图提示（B6）。</li>
 * <li>原值只经 Vault；本库只存 Token、脱敏户名、末四位与非敏感银行字段。</li>
 */
@Component
@AllArgsConstructor
public class SubmitBeneficiaryAccountProcessor
    extends AbstractLockProcessor<SubmitBeneficiaryAccountRequest, SubmitBeneficiaryAccountResponse> {
    /** 账户编号前缀。 */
    static final String CODE_PREFIX = "BA-";

    private final Clock clock;
    private final IdGenerator idGenerator;
    private final BeneficiaryVaultClient beneficiaryVaultClient;
    private final BeneficiaryFieldValidator beneficiaryFieldValidator;
    private final BeneficiaryAccountService beneficiaryAccountService;
    private final BeneficiaryAccountRepository beneficiaryAccountRepository;

    /** Local Partner 的收款账户只能由运营代提交（Q-4，Partner 门户本期不做）。 */
    @Override
    protected void validate(SubmitBeneficiaryAccountRequest request) {
        if (request.getActorType() == BeneficiaryActorType.SYSTEM) {
            throw new BizCheckFailException("收款账户提交人只能是 Client 成员或运营");
        }
        if (request.getOwnerType() == BeneficiaryOwnerType.LOCAL_PARTNER
            && request.getActorType() != BeneficiaryActorType.OPERATIONS) {
            throw new BizCheckFailException("Local Partner 的收款账户只能由运营代提交: " + request.getOwnerId());
        }
    }

    /** 锁 Owner。 */
    @Override
    protected LockCommand lockCommand(SubmitBeneficiaryAccountRequest request) {
        return LockCommand.tryLock(PayoutLocks.beneficiaryOwner(request.getOwnerType(), request.getOwnerId()),
            PayoutLocks.WAIT_MILLIS, PayoutLocks.LEASE_MILLIS);
    }

    /** 校验字段、Protect 原值、查重并保存。 */
    @Override
    protected SubmitBeneficiaryAccountResponse processWithinLock(SubmitBeneficiaryAccountRequest request) {
        // 业务验证
        BeneficiaryAccountDetails details = beneficiaryFieldValidator.normalize(findRoute(request), request.getDetails());

        // 事务外 Vault Protect
        String scope = ownerScope(request);
        String vaultJson = details.vaultJson();
        String detailsToken = beneficiaryVaultClient.protectDetails(vaultJson,
            BeneficiaryContents.detailsIdempotencyKey(scope, vaultJson));
        String accountToken = beneficiaryVaultClient.protectAccountKey(details.accountIdentifier(),
            BeneficiaryContents.accountIdempotencyKey(scope, details.accountIdentifier()));
        BeneficiaryAccount account = buildAccount(request, details, detailsToken, accountToken);

        // 业务防重
        BeneficiaryAccount existing = beneficiaryAccountRepository.findActiveByOwnerToken(request.getOwnerType(),
            request.getOwnerId(), accountToken);
        if (existing != null) {
            return duplicate(existing, account);
        }

        // 本地保存
        beneficiaryAccountService.submit(account, request.getSourceKey());
        return new SubmitBeneficiaryAccountResponse(new BeneficiaryAccountView(account, false, null, null), true);
    }

    /** 收款方式 × 国家 × 币种必须在字段清单中（B8）。 */
    RouteRequirement findRoute(SubmitBeneficiaryAccountRequest request) {
        RouteRequirement route = beneficiaryFieldValidator.findRoute(request.getRailType(), request.getBankCountry(),
            request.getCurrency());
        if (route == null) {
            throw new FailException(PayoutErrorCode.BENEFICIARY_UNSUPPORTED_ROUTE, "该国家 / 币种暂不支持: "
                + request.getRailType().getCode() + " " + request.getBankCountry() + " " + request.getCurrency());
        }
        return route;
    }

    /** 同 Owner 同账号已有未终结账户：同内容返回原账户，否则 409。 */
    SubmitBeneficiaryAccountResponse duplicate(BeneficiaryAccount existing, BeneficiaryAccount submitted) {
        if (existing.getContentHash().equals(submitted.getContentHash())) {
            return new SubmitBeneficiaryAccountResponse(
                new BeneficiaryAccountView(existing, existing.usableAt(clock.instant()), null, null), false);
        }
        throw new FailException(PayoutErrorCode.BENEFICIARY_DUPLICATE, "同一 Owner 已有相同账号的收款账户: " + existing.getId()
            + ", 状态 " + existing.getStatus().getCode());
    }

    /** Owner 与路由的稳定描述，用于派生 Vault 幂等键（不含原值）。 */
    static String ownerScope(SubmitBeneficiaryAccountRequest request) {
        return String.join(":", request.getOwnerType().getCode(), request.getOwnerId().toString(),
            request.getRailType().getCode(), request.getBankCountry(), request.getCurrency());
    }

    /** PENDING_REVIEW 新账户：编号递增，摘要覆盖冻结字段与 Token。 */
    BeneficiaryAccount buildAccount(SubmitBeneficiaryAccountRequest request, BeneficiaryAccountDetails details,
        String detailsToken, String accountToken) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        BeneficiaryAccount account = new BeneficiaryAccount();
        account.setId(idGenerator.nextId());
        account.setOwnerType(request.getOwnerType());
        account.setOwnerId(request.getOwnerId());
        account.setAccountCode(nextAccountCode(request));
        account.setRailType(request.getRailType());
        account.setBankCountry(request.getBankCountry());
        account.setCurrency(request.getCurrency());
        account.setBankName(details.getBankName());
        account.setBankCode(details.bankCodeColumn());
        account.setBeneficiaryVaultToken(detailsToken);
        account.setBankAccountToken(accountToken);
        account.setHolderNameMasked(details.maskedHolderName());
        account.setAccountLast4(details.last4());
        account.setSubmittedByType(request.getActorType());
        account.setSubmittedBy(request.getActorId());
        account.setIsDefault(false);
        account.setStatus(BeneficiaryAccountStatus.PENDING_REVIEW);
        account.setVersion(0L);
        account.setCreateTime(now);
        account.setUpdateTime(now);
        account.setContentHash(BeneficiaryContents.contentHash(account));
        return account;
    }

    /** Owner 内下一个编号 BA-0001 起（锁内取最大值加一）。 */
    String nextAccountCode(SubmitBeneficiaryAccountRequest request) {
        String max = beneficiaryAccountRepository.findMaxAccountCode(request.getOwnerType(), request.getOwnerId());
        long next = max == null ? 1 : Long.parseLong(max.substring(CODE_PREFIX.length())) + 1;
        return CODE_PREFIX + String.format("%04d", next);
    }
}
