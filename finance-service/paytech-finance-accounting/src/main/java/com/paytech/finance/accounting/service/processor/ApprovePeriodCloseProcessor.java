package com.paytech.finance.accounting.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.finance.accounting.core.constant.AccountingLocks;
import com.paytech.finance.accounting.core.dal.dataobject.CloseApproval;
import com.paytech.finance.accounting.core.dal.dataobject.PeriodClose;
import com.paytech.finance.accounting.core.enums.ApprovalDecision;
import com.paytech.finance.accounting.core.enums.ApprovalRole;
import com.paytech.finance.accounting.core.enums.PeriodCloseStatus;
import com.paytech.finance.accounting.domain.PeriodCloseContext;
import com.paytech.finance.accounting.domain.view.PeriodCloseView;
import com.paytech.finance.accounting.domainservice.service.PeriodCloseService;
import com.paytech.finance.accounting.service.processor.request.ReviewPeriodCloseRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 批准期间关账：复验账簿证据后流程 COMPLETED、释放活动槽、期间 CLOSING → CLOSED
 * <li>与发起关账共用期间资源锁；流程状态 CAS 与期间行锁仍是最终防线。</li>
 */
@Component
@AllArgsConstructor
public class ApprovePeriodCloseProcessor extends AbstractLockProcessor<ReviewPeriodCloseRequest, PeriodCloseView> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final PeriodCloseService periodCloseService;

    /** 与同一期间的发起关账共用资源锁，未取得立即返回并发冲突。 */
    @Override
    protected LockCommand lockCommand(ReviewPeriodCloseRequest request) {
        return LockCommand.tryLock(AccountingLocks.periodClose(request.getPeriodId()));
    }

    @Override
    protected PeriodCloseView processWithinLock(ReviewPeriodCloseRequest request) {
        PeriodCloseContext context = periodCloseService.loadForReview(request.getPeriodId(), request.getCloseNo());
        checkReviewable(context.getClose(), request);
        periodCloseService.approve(context, buildApproval(context.getClose(), request.getCheckerId()));
        return periodCloseService.getClose(request.getPeriodId(), request.getCloseNo()).getSummary();
    }

    /** 只能复核 PENDING_CHECK 流程；复核人不同于编制人；确认的摘要等于关账摘要。 */
    void checkReviewable(PeriodClose close, ReviewPeriodCloseRequest request) {
        if (close.getStatus() != PeriodCloseStatus.PENDING_CHECK) {
            throw new BizCheckFailException("关账流程不在待审批状态: " + close.getId() + ", 状态 " + close.getStatus().getCode());
        }
        if (close.getMakerId().equals(request.getCheckerId())) {
            throw new BizCheckFailException("复核人不能与编制人相同: 关账 " + close.getId() + ", 用户 " + request.getCheckerId());
        }
        if (!close.getCloseHash().equals(request.getApprovedCloseHash())) {
            throw new BizCheckFailException("复核确认的关账摘要与流程不一致: " + close.getId());
        }
    }

    /** Checker 对同一关账摘要的批准记录。 */
    CloseApproval buildApproval(PeriodClose close, long checkerId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        CloseApproval approval = new CloseApproval();
        approval.setId(idGenerator.nextId());
        approval.setPeriodCloseId(close.getId());
        approval.setApprovalRole(ApprovalRole.CHECKER);
        approval.setActorId(checkerId);
        approval.setDecision(ApprovalDecision.APPROVED);
        approval.setApprovedCloseHash(close.getCloseHash());
        approval.setDecisionTime(now);
        approval.setCreateTime(now);
        return approval;
    }
}
