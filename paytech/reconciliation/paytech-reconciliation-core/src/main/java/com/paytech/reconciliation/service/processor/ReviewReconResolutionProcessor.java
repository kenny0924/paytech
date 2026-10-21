package com.paytech.reconciliation.service.processor;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.reconciliation.api.enums.CheckerDecision;
import com.paytech.reconciliation.api.enums.ExpectedMovementStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementType;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.ReconLayer;
import com.paytech.reconciliation.api.enums.ResolutionStatus;
import com.paytech.reconciliation.api.enums.ResolutionType;
import com.paytech.reconciliation.core.constant.ReconciliationLocks;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.core.dal.dataobject.ReconResolution;
import com.paytech.reconciliation.domain.BankMatchContext;
import com.paytech.reconciliation.domain.BankMatching;
import com.paytech.reconciliation.domain.ResolutionApplyContext;
import com.paytech.reconciliation.domainservice.repository.BankStatementLineRepository;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementRepository;
import com.paytech.reconciliation.domainservice.repository.ExpectedBankMovementRepository;
import com.paytech.reconciliation.domainservice.repository.ReconExceptionRepository;
import com.paytech.reconciliation.domainservice.repository.ReconResolutionRepository;
import com.paytech.reconciliation.domainservice.service.BankMatchService;
import com.paytech.reconciliation.domainservice.service.BankReconciliationQueryService;
import com.paytech.reconciliation.domainservice.service.ChannelReconciliationResolutionService;
import com.paytech.reconciliation.domainservice.service.ReconExceptionService;
import com.paytech.reconciliation.domainservice.service.ReconResolutionService;
import com.paytech.reconciliation.service.processor.request.ReviewReconResolutionRequest;
import com.paytech.reconciliation.service.processor.response.ReconResolutionResponse;
import lombok.AllArgsConstructor;

/**
 * 复核异常处置（Checker，UC-R16，#27，AC-11、AC-14）：批准即在同一本地事务应用
 * <li>锁 recon-exception:{id} 与应用对象（交易层所属渠道账单；银行流水 / 预期银行流水），tryLocks 统一排序。</li>
 * <li>复核人必须不同于提交人，确认的摘要必须等于 contentHash；同一复核人同一决定重复提交返回当前结果。</li>
 * <li>拒绝：处置 REJECTED，异常回 OPEN。批准：处置 APPLIED、异常 RESOLVED，再按来源对象应用——RE_MATCH（交易层写账单行匹配；银行层新建
 * CONFIRMED 银行匹配，带处置 ID）、ACCEPT_TIMING_DIFFERENCE（只结案；DUPLICATE_SETTLEMENT 交还账单行匹配）、CLASSIFY（账单行归类随对平
 * 事件带出；平台流水结清、预期流水归类、银行流水 MATCHED 并登记 ReconAdjustmentApproved）。交易层应用后登记所属账单的门禁重算。</li>
 * <li>应用条件在锁内重新校验（预期流水与银行流水的归类金额须仍等于当前未完成金额），不满足时整笔回滚并拒绝。</li>
 */
@Component
@AllArgsConstructor
public class ReviewReconResolutionProcessor
    extends AbstractLockProcessor<ReviewReconResolutionRequest, ReconResolutionResponse> {
    private final BankMatchService bankMatchService;
    private final TransactionTemplate transactionTemplate;
    private final ReconExceptionService reconExceptionService;
    private final ReconResolutionService reconResolutionService;
    private final ReconExceptionRepository reconExceptionRepository;
    private final ReconResolutionRepository reconResolutionRepository;
    private final ChannelStatementRepository channelStatementRepository;
    private final BankStatementLineRepository bankStatementLineRepository;
    private final BankReconciliationQueryService bankReconciliationQueryService;
    private final ExpectedBankMovementRepository expectedBankMovementRepository;
    private final ChannelReconciliationResolutionService channelReconciliationResolutionService;

    /** 异常锁与应用对象锁（异常与处置的挂靠关系不可变，锁前读取）。 */
    @Override
    protected LockCommand lockCommand(ReviewReconResolutionRequest request) {
        ReconResolution resolution = loadResolution(request.getResolutionId());
        ReconException exception = reconExceptionRepository.findById(resolution.getExceptionId());
        List<String> keys = new ArrayList<>(List.of(ReconciliationLocks.reconExceptionKey(exception.getId())));
        if (exception.getStatementId() != null) {
            keys.addAll(ReconciliationLocks.channelStatement(exception.getStatementId()));
        }
        if (exception.getSourceType() == ReconExceptionSourceType.BANK_STATEMENT_LINE) {
            keys.addAll(ReconciliationLocks.bankMatch(exception.getSourceId(), resolution.getRematchTargetId() == null
                ? List.of() : List.of(resolution.getRematchTargetId())));
        }
        if (exception.getSourceType() == ReconExceptionSourceType.EXPECTED_BANK_MOVEMENT) {
            keys.add(ReconciliationLocks.expectedBankMovement(exception.getSourceId()));
        }
        return LockCommand.tryLocks(keys, ReconciliationLocks.WAIT, ReconciliationLocks.LEASE);
    }

    @Override
    protected ReconResolutionResponse processWithinLock(ReviewReconResolutionRequest request) {
        // 查询原处置
        ReconResolution resolution = loadResolution(request.getResolutionId());
        ReconException exception = reconExceptionRepository.findById(resolution.getExceptionId());
        if (resolution.getStatus() != ResolutionStatus.PENDING_CHECK) {
            return replay(resolution, exception, request);
        }
        checkReviewer(resolution, exception, request);

        // 拒绝
        if (request.getDecision() == CheckerDecision.REJECTED) {
            transactionTemplate.executeWithoutResult(status -> {
                reconResolutionService.reject(resolution, request.getActorId(), request.getApprovedContentHash());
                reconExceptionService.reopen(exception);
            });
            return new ReconResolutionResponse(resolution, exception);
        }

        // 批准：构建应用上下文并在同一事务应用
        ResolutionApplyContext context = buildApplyContext(resolution, exception, request);
        transactionTemplate.executeWithoutResult(status -> approveAndApply(context, request));
        return new ReconResolutionResponse(resolution, exception);
    }

    /** 读取处置，不存在为业务拒绝。 */
    ReconResolution loadResolution(Long resolutionId) {
        ReconResolution resolution = reconResolutionRepository.findById(resolutionId);
        if (resolution == null) {
            throw new BizCheckFailException("异常处置不存在: " + resolutionId);
        }
        return resolution;
    }

    /** 已复核：同一复核人同一决定返回当前结果，其余拒绝。 */
    static ReconResolutionResponse replay(ReconResolution resolution, ReconException exception,
        ReviewReconResolutionRequest request) {
        if (request.getActorId().equals(resolution.getCheckerId())
            && request.getDecision() == resolution.getCheckerDecision()) {
            return new ReconResolutionResponse(resolution, exception);
        }
        throw new BizCheckFailException("异常处置不是待复核状态: " + resolution.getId() + ", 状态 "
            + resolution.getStatus().getCode());
    }

    /** 复核人不同于提交人，摘要一致，异常仍在复核中。 */
    static void checkReviewer(ReconResolution resolution, ReconException exception,
        ReviewReconResolutionRequest request) {
        if (resolution.getMakerId().equals(request.getActorId())) {
            throw new BizCheckFailException("提交人不能复核自己的处置: 处置 " + resolution.getId() + ", 操作人 "
                + request.getActorId());
        }
        if (!resolution.getContentHash().equals(request.getApprovedContentHash())) {
            throw new BizCheckFailException("确认的内容摘要与提交不一致，请刷新后重新复核: 处置 " + resolution.getId());
        }
        if (exception.getStatus() != ReconExceptionStatus.UNDER_REVIEW) {
            throw new BizCheckFailException("对账异常不在复核中: " + exception.getId() + ", 状态 "
                + exception.getStatus().getCode());
        }
    }

    /** 银行层应用对象在锁内读取并校验；交易层由应用服务在事务内校验。 */
    ResolutionApplyContext buildApplyContext(ReconResolution resolution, ReconException exception,
        ReviewReconResolutionRequest request) {
        if (exception.getReconLayer() == ReconLayer.TRANSACTION) {
            return new ResolutionApplyContext(resolution, exception, null, null, null, null);
        }
        if (resolution.getResolutionType() == ResolutionType.RE_MATCH) {
            BankMatchContext match = bankReconciliationQueryService.loadMatchContext(exception.getSourceId(), List.of(
                new BankMatchContext.AllocationInput(resolution.getRematchTargetId(), resolution.getRematchAmount(), null)),
                MatchMethod.MANUAL, request.getActorId(), resolution.getId());
            match.check();
            return new ResolutionApplyContext(resolution, exception, match, null, null, null);
        }
        if (resolution.getResolutionType() != ResolutionType.CLASSIFY) {
            return new ResolutionApplyContext(resolution, exception, null, null, null, null);
        }
        return exception.getSourceType() == ReconExceptionSourceType.EXPECTED_BANK_MOVEMENT
            ? movementClassification(resolution, exception) : lineClassification(resolution, exception);
    }

    /** 预期流水归类：金额须仍等于当前剩余金额。 */
    ResolutionApplyContext movementClassification(ReconResolution resolution, ReconException exception) {
        ExpectedBankMovement movement = expectedBankMovementRepository.findById(exception.getSourceId());
        if (BankMatching.movementRemaining(movement).compareTo(resolution.getAmount()) != 0) {
            throw new BizCheckFailException("预期银行流水的剩余金额已变化，归类金额不再等于剩余: 处置 " + resolution.getId()
                + ", 剩余 " + BankMatching.movementRemaining(movement).toPlainString());
        }
        ChannelStatement statement = movement.getMovementType() == ExpectedMovementType.CHANNEL_SETTLEMENT
            ? channelStatementRepository.findById(movement.getSourceId()) : null;
        return new ResolutionApplyContext(resolution, exception, null, movement, statement, null);
    }

    /** 银行流水归类：金额须仍等于当前带符号未匹配金额。 */
    ResolutionApplyContext lineClassification(ReconResolution resolution, ReconException exception) {
        BankStatementLine line = bankStatementLineRepository.findById(exception.getSourceId());
        if (line.getMatchStatus() == LineMatchStatus.MATCHED
            || BankMatching.lineSignedRemaining(line).compareTo(resolution.getAmount()) != 0) {
            throw new BizCheckFailException("银行流水的未匹配金额已变化，归类金额不再等于未匹配金额: 处置 " + resolution.getId());
        }
        return new ResolutionApplyContext(resolution, exception, null, null, null, line);
    }

    /** 事务内：处置 APPLIED、异常 RESOLVED、按来源对象应用、交易层登记门禁重算。 */
    void approveAndApply(ResolutionApplyContext context, ReviewReconResolutionRequest request) {
        ReconResolution resolution = context.getResolution();
        ReconException exception = context.getException();
        reconResolutionService.approve(resolution, exception, request.getActorId(), request.getApprovedContentHash());
        reconExceptionService.resolve(exception);
        switch (resolution.getResolutionType()) {
            case RE_MATCH -> applyRematch(context);
            case ACCEPT_TIMING_DIFFERENCE -> applyAccept(exception);
            case CLASSIFY -> applyClassification(context);
        }
        if (exception.getReconLayer() == ReconLayer.TRANSACTION && exception.getStatementId() != null) {
            channelReconciliationResolutionService.scheduleStatementEvaluation(exception.getStatementId(),
                resolution.getId());
        }
    }

    /** RE_MATCH：交易层写账单行匹配；银行层确认新匹配，预期流水因此满额时自动结案其金额差异异常。 */
    void applyRematch(ResolutionApplyContext context) {
        ReconException exception = context.getException();
        if (exception.getReconLayer() == ReconLayer.TRANSACTION) {
            channelReconciliationResolutionService.applyLineRematch(exception.getSourceId(),
                context.getResolution().getRematchTargetId(), context.getResolution().getId());
            return;
        }
        bankMatchService.confirm(context.getBankMatch());
        ExpectedBankMovement movement = context.getBankMatch().getAllocations().getFirst().getMovement();
        if (movement.getStatus() == ExpectedMovementStatus.CONFIRMED) {
            reconExceptionService.autoClose(ReconExceptionSourceType.EXPECTED_BANK_MOVEMENT, movement.getId(),
                ReconExceptionType.BANK_AMOUNT_MISMATCH);
        }
    }

    /** ACCEPT_TIMING_DIFFERENCE：只结案；重复结算确认不是重复时交还账单行匹配。 */
    void applyAccept(ReconException exception) {
        if (exception.getExceptionType() == ReconExceptionType.DUPLICATE_SETTLEMENT) {
            channelReconciliationResolutionService.releaseLineForRematching(exception.getSourceId());
        }
    }

    /** CLASSIFY：按来源对象入账路径应用（设计 F3）。 */
    void applyClassification(ResolutionApplyContext context) {
        ReconException exception = context.getException();
        ReconResolution resolution = context.getResolution();
        switch (exception.getSourceType()) {
            case CHANNEL_STATEMENT_LINE -> channelReconciliationResolutionService.applyLineClassification(
                exception.getSourceId(), resolution.getId());
            case PLATFORM_TXN -> channelReconciliationResolutionService.applyPlatformTxnClassification(
                exception.getSourceId(), resolution.getAmount());
            case EXPECTED_BANK_MOVEMENT -> bankMatchService.classifyMovement(context.getMovement(),
                context.getChannelStatement(), resolution.getAmount());
            case BANK_STATEMENT_LINE -> bankMatchService.classifyLine(context.getBankLine());
            default -> throw new BizCheckFailException("来源对象不能归类: " + exception.getSourceType().getCode());
        }
    }
}
