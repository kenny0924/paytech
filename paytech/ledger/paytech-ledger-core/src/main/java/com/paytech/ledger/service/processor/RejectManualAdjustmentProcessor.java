package com.paytech.ledger.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.ledger.core.constant.AccountingLocks;
import com.paytech.ledger.core.dal.dataobject.AdjustmentApproval;
import com.paytech.ledger.core.dal.dataobject.ManualAdjustment;
import com.paytech.ledger.core.enums.AdjustmentStatus;
import com.paytech.ledger.core.enums.ApprovalDecision;
import com.paytech.ledger.core.enums.ApprovalRole;
import com.paytech.ledger.domainservice.repository.AdjustmentApprovalRepository;
import com.paytech.ledger.domainservice.repository.ManualAdjustmentRepository;
import com.paytech.ledger.domainservice.service.ManualAdjustmentService;
import com.paytech.ledger.domainservice.validator.adjustment.ManualAdjustmentReviewValidator;
import com.paytech.ledger.service.processor.request.ReviewManualAdjustmentRequest;
import com.paytech.ledger.service.processor.response.ReviewManualAdjustmentResponse;

import lombok.AllArgsConstructor;

/**
 * 驳回人工调整
 * <li>与批准共用调整资源锁；状态迁移以版本 CAS 保护。驳回后不可再审批，修正需新建调整。</li>
 * <li>同一复核人以同一摘要重复驳回时返回当前结果，其他重复复核拒绝。</li>
 */
@Component
@AllArgsConstructor
public class RejectManualAdjustmentProcessor
    extends AbstractLockProcessor<ReviewManualAdjustmentRequest, ReviewManualAdjustmentResponse> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final ManualAdjustmentService manualAdjustmentService;
    private final ManualAdjustmentRepository manualAdjustmentRepository;
    private final AdjustmentApprovalRepository adjustmentApprovalRepository;

    /** 与同一资源的其他写入口共用资源锁，未取得立即返回并发冲突。 */
    @Override
    protected LockCommand lockCommand(ReviewManualAdjustmentRequest request) {
        return LockCommand.tryLock(AccountingLocks.manualAdjustment(request.getManualAdjustmentId()));
    }

    @Override
    protected ReviewManualAdjustmentResponse processWithinLock(ReviewManualAdjustmentRequest request) {
        // 查询原复核
        ManualAdjustment adjustment = manualAdjustmentRepository.findById(request.getManualAdjustmentId());
        if (adjustment == null) {
            throw new BizCheckFailException("人工调整不存在: " + request.getManualAdjustmentId());
        }
        if (adjustment.getStatus() == AdjustmentStatus.REJECTED && isSameRejection(adjustment, request)) {
            return response(adjustment);
        }

        // 驳回
        ManualAdjustmentReviewValidator.checkReviewable(adjustment, request.getCheckerId(),
            request.getApprovedContentHash());
        manualAdjustmentService.reject(adjustment, buildCheckerRejection(adjustment, request.getCheckerId()));
        return response(adjustment);
    }

    /** 判断是否同一复核人以同一摘要作出的驳回重放。 */
    boolean isSameRejection(ManualAdjustment adjustment, ReviewManualAdjustmentRequest request) {
        return ManualAdjustmentReviewValidator.isSameReview(
            adjustmentApprovalRepository.findByAdjustment(adjustment.getId()), request.getCheckerId(),
            ApprovalDecision.REJECTED, request.getApprovedContentHash());
    }

    /** Checker 对同一内容摘要的驳回记录。 */
    AdjustmentApproval buildCheckerRejection(ManualAdjustment adjustment, long checkerId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        AdjustmentApproval approval = new AdjustmentApproval();
        approval.setId(idGenerator.nextId());
        approval.setAdjustmentId(adjustment.getId());
        approval.setApprovalRole(ApprovalRole.CHECKER);
        approval.setActorId(checkerId);
        approval.setDecision(ApprovalDecision.REJECTED);
        approval.setApprovedContentHash(adjustment.getContentHash());
        approval.setDecisionTime(now);
        approval.setCreateTime(now);
        return approval;
    }

    /** 从调整当前状态构造响应；驳回没有分录。 */
    ReviewManualAdjustmentResponse response(ManualAdjustment adjustment) {
        ReviewManualAdjustmentResponse response = new ReviewManualAdjustmentResponse();
        response.setManualAdjustmentId(adjustment.getId());
        response.setStatus(adjustment.getStatus());
        return response;
    }
}
