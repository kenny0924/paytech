package com.paytech.payout.domainservice.repository.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.api.enums.BeneficiaryReviewStage;
import com.paytech.payout.api.enums.CheckerDecision;
import com.paytech.payout.api.enums.MakerReviewDecision;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;
import com.paytech.payout.core.dal.mapper.BeneficiaryAccountMapper;
import com.paytech.payout.domainservice.repository.BeneficiaryAccountRepository;

import lombok.AllArgsConstructor;

/** BeneficiaryAccountRepository 的 MyBatis 实现：条件更新先确认行数，成功后才同步传入对象。 */
@Repository
@AllArgsConstructor
public class BeneficiaryAccountRepositoryImpl implements BeneficiaryAccountRepository {
    private final Clock clock;
    private final BeneficiaryAccountMapper mapper;

    /** 插入新账户。 */
    @Override
    public void insert(BeneficiaryAccount record) {
        mapper.insert(record);
    }

    /** 按主键读取。 */
    @Override
    public BeneficiaryAccount findById(Long id) {
        return mapper.selectById(id);
    }

    /** 按 Owner 读取。 */
    @Override
    public List<BeneficiaryAccount> listByOwner(BeneficiaryOwnerType ownerType, Long ownerId,
        BeneficiaryAccountStatus status, String currency) {
        return mapper.selectByOwner(ownerType, ownerId, status, currency);
    }

    /** 同 Owner 同账号的未终结账户。 */
    @Override
    public BeneficiaryAccount findActiveByOwnerToken(BeneficiaryOwnerType ownerType, Long ownerId,
        String bankAccountToken) {
        return mapper.selectActiveByOwnerToken(ownerType, ownerId, bankAccountToken);
    }

    /** 同 Owner 当前最大的账户编号。 */
    @Override
    public String findMaxAccountCode(BeneficiaryOwnerType ownerType, Long ownerId) {
        return mapper.selectMaxAccountCode(ownerType, ownerId);
    }

    /** 同 Owner、同币种的当前默认账户。 */
    @Override
    public BeneficiaryAccount findDefault(BeneficiaryOwnerType ownerType, Long ownerId, String currency) {
        return mapper.selectDefault(ownerType, ownerId, currency);
    }

    /** 其他 Owner 以同一账号持有的未终结账户。 */
    @Override
    public List<BeneficiaryAccount> listSharedAccounts(BeneficiaryAccount account, int limit) {
        return mapper.selectOtherOwnerAccounts(account.getBankAccountToken(), account.getOwnerType(), account.getOwnerId(),
            limit);
    }

    /** 核验队列分页。 */
    @Override
    public List<BeneficiaryAccount> findReviewPage(BeneficiaryReviewStage stage, BeneficiaryOwnerType ownerType,
        Long ownerId, Instant decidedSince, long offset, int limit) {
        return mapper.selectReviewPage(stage, ownerType, ownerId, decidedSince, offset, limit);
    }

    /** 核验队列总数。 */
    @Override
    public long countReviewPage(BeneficiaryReviewStage stage, BeneficiaryOwnerType ownerType, Long ownerId,
        Instant decidedSince) {
        return mapper.countReviewPage(stage, ownerType, ownerId, decidedSince);
    }

    /** 记录 Maker 意见。 */
    @Override
    public void recordMakerReview(BeneficiaryAccount record, Long makerId, MakerReviewDecision decision,
        String reasonCode, String makerNote, String reasonNote) {
        Instant now = now();
        checkUpdated(mapper.updateMakerReview(record, makerId, decision, reasonCode, makerNote, reasonNote, now), record);
        record.setReviewMakerId(makerId);
        record.setReviewMakerDecision(decision);
        record.setReviewMakerNote(makerNote);
        record.setReasonCode(reasonCode);
        record.setReasonNote(reasonNote);
        advance(record, now);
    }

    /** 记录 Checker 决定。 */
    @Override
    public void recordCheckerDecision(BeneficiaryAccount record, Long checkerId, CheckerDecision decision,
        String reasonCode, String reasonNote, Duration cooling) {
        Instant now = now();
        BeneficiaryAccountStatus status = decision == CheckerDecision.APPROVED ? BeneficiaryAccountStatus.VERIFIED
            : BeneficiaryAccountStatus.REJECTED;
        Instant coolingUntil = status == BeneficiaryAccountStatus.VERIFIED ? now.plus(cooling) : null;
        checkUpdated(mapper.updateCheckerDecision(record, status, checkerId, decision, reasonCode, reasonNote, coolingUntil,
            now), record);
        record.setStatus(status);
        record.setCheckerId(checkerId);
        record.setCheckerDecision(decision);
        record.setDecisionTime(now);
        record.setReasonCode(reasonCode);
        record.setReasonNote(reasonNote);
        record.setCoolingUntil(coolingUntil);
        advance(record, now);
    }

    /** 设置或清除默认标记。 */
    @Override
    public void updateDefault(BeneficiaryAccount record, boolean isDefault) {
        Instant now = now();
        checkUpdated(mapper.updateDefault(record, isDefault, now), record);
        record.setIsDefault(isDefault);
        advance(record, now);
    }

    /** 停用或关闭。 */
    @Override
    public void updateStatusWithReason(BeneficiaryAccount record, BeneficiaryAccountStatus status, String reasonCode) {
        Instant now = now();
        checkUpdated(mapper.updateStatusWithReason(record, status, reasonCode, now), record);
        record.setStatus(status);
        record.setReasonCode(reasonCode);
        record.setReasonNote(null);
        record.setIsDefault(false);
        advance(record, now);
    }

    /** 恢复重新核验。 */
    @Override
    public void resume(BeneficiaryAccount record) {
        Instant now = now();
        checkUpdated(mapper.updateResume(record, now), record);
        record.setStatus(BeneficiaryAccountStatus.PENDING_REVIEW);
        record.setReviewMakerId(null);
        record.setReviewMakerDecision(null);
        record.setReviewMakerNote(null);
        record.setCheckerId(null);
        record.setCheckerDecision(null);
        record.setDecisionTime(null);
        record.setReasonCode(null);
        record.setReasonNote(null);
        record.setCoolingUntil(null);
        advance(record, now);
    }

    /** 条件更新必须命中一行，否则账户已被并发修改。 */
    private static void checkUpdated(int rows, BeneficiaryAccount record) {
        if (rows != 1) {
            throw new ConcurrentRequestException("收款账户已变更，请刷新后重试: " + record.getId());
        }
    }

    /** 同步版本与更新时间。 */
    private static void advance(BeneficiaryAccount record, Instant now) {
        record.setVersion(record.getVersion() + 1);
        record.setUpdateTime(now);
    }

    /** 写入时间：UTC 微秒，与 DATETIME(6) 一致。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
