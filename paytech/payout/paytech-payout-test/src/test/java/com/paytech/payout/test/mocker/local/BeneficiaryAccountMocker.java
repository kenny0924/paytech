package com.paytech.payout.test.mocker.local;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryActorType;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.api.enums.BeneficiaryRailType;
import com.paytech.payout.api.enums.CheckerDecision;
import com.paytech.payout.api.enums.MakerReviewDecision;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.core.dal.mapper.BeneficiaryAccountMapper;
import com.paytech.payout.test.support.PayoutTestConstants;

/**
 * 本地收款账户前置数据：经生产 Mapper 直接插入已核验 / 冷却中等状态的账户，不调用被测命令
 * <li>Token 为合成占位（不经 Vault），只用于余额解释与默认账户等不需要原值的场景。</li>
 */
@TestComponent
public class BeneficiaryAccountMocker {
    private static final AtomicLong SEQUENCE = new AtomicLong(910_000_000_000L);

    private final BeneficiaryAccountMapper beneficiaryAccountMapper;

    /** 以生产 Mapper 构造。 */
    public BeneficiaryAccountMocker(BeneficiaryAccountMapper beneficiaryAccountMapper) {
        this.beneficiaryAccountMapper = beneficiaryAccountMapper;
    }

    /** 插入一个 VERIFIED 账户：coolingUntil 为给定时刻（过去即可用，未来即冷却中）。 */
    public BeneficiaryAccount verified(BeneficiaryOwnerType ownerType, long ownerId, String currency, Instant coolingUntil,
        boolean isDefault) {
        BeneficiaryAccount account = template(ownerType, ownerId, currency, BeneficiaryAccountStatus.VERIFIED);
        account.setReviewMakerId(PayoutTestConstants.MAKER_ID);
        account.setReviewMakerDecision(MakerReviewDecision.APPROVE);
        account.setCheckerId(PayoutTestConstants.CHECKER_ID);
        account.setCheckerDecision(CheckerDecision.APPROVED);
        account.setDecisionTime(coolingUntil.minus(24, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS));
        account.setCoolingUntil(coolingUntil.truncatedTo(ChronoUnit.MICROS));
        account.setIsDefault(isDefault);
        beneficiaryAccountMapper.insert(account);
        return account;
    }

    /** 插入一个给定状态的未核验类账户（PENDING_REVIEW / REJECTED / CLOSED）。 */
    public BeneficiaryAccount withStatus(BeneficiaryOwnerType ownerType, long ownerId, String currency,
        BeneficiaryAccountStatus status) {
        BeneficiaryAccount account = template(ownerType, ownerId, currency, status);
        beneficiaryAccountMapper.insert(account);
        return account;
    }

    /** 合法默认账户（不含原值）。 */
    static BeneficiaryAccount template(BeneficiaryOwnerType ownerType, long ownerId, String currency,
        BeneficiaryAccountStatus status) {
        long id = SEQUENCE.incrementAndGet();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        BeneficiaryAccount account = new BeneficiaryAccount();
        account.setId(id);
        account.setOwnerType(ownerType);
        account.setOwnerId(ownerId);
        account.setAccountCode("BA-" + (9000 + id % 1000));
        account.setRailType(BeneficiaryRailType.LOCAL);
        account.setBankCountry("AE");
        account.setCurrency(currency);
        account.setBankName(PayoutTestConstants.BANK_NAME);
        account.setBeneficiaryVaultToken(("ref" + id + "x".repeat(43)).substring(0, 43));
        account.setBankAccountToken(("key" + id + "y".repeat(43)).substring(0, 43));
        account.setHolderNameMasked(PayoutTestConstants.HOLDER_NAME_MASKED);
        account.setAccountLast4(String.format("%04d", id % 10000));
        account.setContentHash("0".repeat(63) + "1");
        account.setSubmittedByType(BeneficiaryActorType.CLIENT_MEMBER);
        account.setSubmittedBy(PayoutTestConstants.CLIENT_MEMBER_ID);
        account.setIsDefault(false);
        account.setStatus(status);
        account.setVersion(0L);
        account.setCreateTime(now);
        account.setUpdateTime(now);
        return account;
    }
}
