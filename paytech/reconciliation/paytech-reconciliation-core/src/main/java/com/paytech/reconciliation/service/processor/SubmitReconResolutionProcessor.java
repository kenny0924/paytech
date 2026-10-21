package com.paytech.reconciliation.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.ReconLayer;
import com.paytech.reconciliation.api.enums.RematchTargetType;
import com.paytech.reconciliation.api.enums.ResolutionStatus;
import com.paytech.reconciliation.api.enums.ResolutionType;
import com.paytech.reconciliation.core.constant.ReconciliationLocks;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.core.dal.dataobject.ReconResolution;
import com.paytech.reconciliation.domain.Amounts;
import com.paytech.reconciliation.domain.BankMatchContext;
import com.paytech.reconciliation.domain.ReconResolutionRules;
import com.paytech.reconciliation.domainservice.repository.PlatformTxnRepository;
import com.paytech.reconciliation.domainservice.repository.ReconExceptionRepository;
import com.paytech.reconciliation.domainservice.repository.ReconResolutionRepository;
import com.paytech.reconciliation.domainservice.service.BankReconciliationQueryService;
import com.paytech.reconciliation.domainservice.service.ReconExceptionService;
import com.paytech.reconciliation.domainservice.service.ReconResolutionService;
import com.paytech.reconciliation.service.processor.request.SubmitReconResolutionRequest;
import com.paytech.reconciliation.service.processor.response.ReconResolutionResponse;
import lombok.AllArgsConstructor;

/**
 * 提交异常处置（Maker，UC-R15，#26，AC-11）：PENDING_CHECK 处置 + 异常 OPEN → UNDER_REVIEW，同一事务
 * <li>锁 recon-exception:{id}，与复核共用；异常须为 OPEN（一个异常只有一条最终 APPLIED 处置）。</li>
 * <li>处置方式按异常类型限定、归类码按来源对象限定（{@link ReconResolutionRules}）；CLASSIFY 金额必须等于异常差异金额。</li>
 * <li>RE_MATCH：交易层目标为同一清算账户的平台流水，不带分配金额；银行层目标为同一银行账户的预期银行流水，分配金额按人工多选匹配的
 * 不变量预校验（批准时在锁内再校验一次）。</li>
 * <li>内容摘要覆盖异常 ID、处置方式与全部输入，复核人须原样确认。</li>
 */
@Component
@AllArgsConstructor
public class SubmitReconResolutionProcessor
    extends AbstractLockProcessor<SubmitReconResolutionRequest, ReconResolutionResponse> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final PlatformTxnRepository platformTxnRepository;
    private final ReconExceptionService reconExceptionService;
    private final ReconResolutionService reconResolutionService;
    private final ReconExceptionRepository reconExceptionRepository;
    private final ReconResolutionRepository reconResolutionRepository;
    private final BankReconciliationQueryService bankReconciliationQueryService;

    /** 对账异常资源锁。 */
    @Override
    protected LockCommand lockCommand(SubmitReconResolutionRequest request) {
        return LockCommand.tryLocks(ReconciliationLocks.reconException(request.getExceptionId()),
            ReconciliationLocks.WAIT, ReconciliationLocks.LEASE);
    }

    @Override
    protected ReconResolutionResponse processWithinLock(SubmitReconResolutionRequest request) {
        // 业务验证
        ReconException exception = loadOpen(request.getExceptionId());
        checkType(exception, request);
        checkFields(exception, request);
        if (request.getResolutionType() == ResolutionType.RE_MATCH) {
            checkRematchTarget(exception, request);
        }

        // 本地保存
        ReconResolution resolution = build(exception, request);
        transactionTemplate.executeWithoutResult(status -> {
            reconResolutionService.submit(resolution);
            reconExceptionService.markUnderReview(exception);
        });
        return new ReconResolutionResponse(resolution, exception);
    }

    /** 异常存在且 OPEN。 */
    ReconException loadOpen(Long exceptionId) {
        ReconException exception = reconExceptionRepository.findById(exceptionId);
        if (exception == null) {
            throw new BizCheckFailException("对账异常不存在: " + exceptionId);
        }
        if (exception.getStatus() != ReconExceptionStatus.OPEN) {
            throw new BizCheckFailException("对账异常不是 OPEN，不能提交处置: " + exceptionId + ", 状态 "
                + exception.getStatus().getCode());
        }
        return exception;
    }

    /** 处置方式在异常类型允许范围内。 */
    static void checkType(ReconException exception, SubmitReconResolutionRequest request) {
        if (!ReconResolutionRules.allowedTypes(exception.getExceptionType()).contains(request.getResolutionType())) {
            throw new BizCheckFailException("异常类型 " + exception.getExceptionType().getCode() + " 不能使用处置方式 "
                + request.getResolutionType().getCode() + ": 异常 " + exception.getId());
        }
    }

    /** 字段组合：CLASSIFY 只带归类码与金额，RE_MATCH 只带目标，ACCEPT 都不带。 */
    static void checkFields(ReconException exception, SubmitReconResolutionRequest request) {
        boolean classify = request.getResolutionType() == ResolutionType.CLASSIFY;
        boolean rematch = request.getResolutionType() == ResolutionType.RE_MATCH;
        String context = "异常 " + exception.getId() + ", 处置方式 " + request.getResolutionType().getCode();
        if (classify != (request.getClassificationCode() != null) || classify != (request.getAmount() != null)) {
            throw new BizCheckFailException("归类码与归类金额只在 CLASSIFY 时填写且必须填写: " + context);
        }
        if (rematch != (request.getRematchTargetType() != null) || rematch != (request.getRematchTargetId() != null)
            || (!rematch && request.getRematchAmount() != null)) {
            throw new BizCheckFailException("重新匹配目标只在 RE_MATCH 时填写且必须填写: " + context);
        }
        if (classify) {
            checkClassification(exception, request);
        }
    }

    /** 归类码按来源对象限定，金额等于差异金额且不超过币种精度。 */
    static void checkClassification(ReconException exception, SubmitReconResolutionRequest request) {
        if (!ReconResolutionRules.allowedCodes(exception.getSourceType()).contains(request.getClassificationCode())) {
            throw new BizCheckFailException("来源对象 " + exception.getSourceType().getCode() + " 不能使用归类码 "
                + request.getClassificationCode().getCode() + ": 异常 " + exception.getId());
        }
        Amounts.checkPrecision(exception.getCurrency(), request.getAmount(), "异常 " + exception.getId() + " 归类金额");
        if (request.getAmount().compareTo(exception.getDifferenceAmount()) != 0) {
            throw new BizCheckFailException("归类金额必须等于异常差异金额: 异常 " + exception.getId() + ", 差异 "
                + exception.getDifferenceAmount().toPlainString() + ", 归类金额 " + request.getAmount().toPlainString());
        }
    }

    /** RE_MATCH 目标：交易层为同清算账户的平台流水，银行层为可分配的预期银行流水。 */
    void checkRematchTarget(ReconException exception, SubmitReconResolutionRequest request) {
        boolean transaction = exception.getReconLayer() == ReconLayer.TRANSACTION;
        RematchTargetType expected = transaction ? RematchTargetType.PLATFORM_TXN : RematchTargetType.EXPECTED_BANK_MOVEMENT;
        if (request.getRematchTargetType() != expected || transaction == (request.getRematchAmount() != null)) {
            throw new BizCheckFailException("重新匹配目标类型或分配金额与对账层不符: 异常 " + exception.getId() + ", 期望目标 "
                + expected.getCode());
        }
        if (transaction) {
            PlatformTxn txn = platformTxnRepository.findById(request.getRematchTargetId());
            if (txn == null || !txn.getClearingAccountId().equals(exception.getClearingAccountId())) {
                throw new BizCheckFailException("重新匹配的平台流水不存在或不属于同一清算账户: 异常 " + exception.getId()
                    + ", 平台流水 " + request.getRematchTargetId());
            }
            return;
        }
        bankReconciliationQueryService.loadMatchContext(exception.getSourceId(), List.of(
            new BankMatchContext.AllocationInput(request.getRematchTargetId(), request.getRematchAmount(), null)),
            MatchMethod.MANUAL, request.getActorId(), null).check();
    }

    /** PENDING_CHECK 处置，序号接在已有处置之后。 */
    ReconResolution build(ReconException exception, SubmitReconResolutionRequest request) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ReconResolution resolution = new ReconResolution();
        resolution.setId(idGenerator.nextId());
        resolution.setExceptionId(exception.getId());
        resolution.setResolutionNo(reconResolutionRepository.listByException(exception.getId()).size() + 1);
        resolution.setResolutionType(request.getResolutionType());
        resolution.setClassificationCode(request.getClassificationCode());
        resolution.setAmount(request.getAmount());
        resolution.setRematchTargetType(request.getRematchTargetType());
        resolution.setRematchTargetId(request.getRematchTargetId());
        resolution.setRematchAmount(request.getRematchAmount());
        resolution.setNote(request.getNote());
        resolution.setContentHash(ReconResolutionRules.contentHash(resolution));
        resolution.setMakerId(request.getActorId());
        resolution.setStatus(ResolutionStatus.PENDING_CHECK);
        resolution.setVersion(1L);
        resolution.setCreateTime(now);
        resolution.setUpdateTime(now);
        return resolution;
    }
}
