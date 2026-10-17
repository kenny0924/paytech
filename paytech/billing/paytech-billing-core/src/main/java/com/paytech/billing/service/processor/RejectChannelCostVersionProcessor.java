package com.paytech.billing.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.billing.core.constant.BillingLocks;
import com.paytech.billing.domain.ChannelCostVersionContext;
import com.paytech.billing.domain.view.ChannelCostVersionView;
import com.paytech.billing.domainservice.service.ChannelCostPlanService;
import com.paytech.billing.domainservice.validator.cost.ChannelCostVersionReviewValidator;
import com.paytech.billing.service.processor.request.ReviewChannelCostVersionRequest;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 驳回渠道成本版本：PENDING_APPROVAL → REJECTED；Checker ≠ Maker，确认摘要等于版本 contentHash
 * <li>与同一方案的其他写入口共用资源锁并持续等待；事务内先锁方案再锁版本，状态以原状态条件更新。</li>
 */
@Component
@AllArgsConstructor
public class RejectChannelCostVersionProcessor
    extends AbstractLockProcessor<ReviewChannelCostVersionRequest, ChannelCostVersionView> {
    private final ChannelCostPlanService channelCostPlanService;

    /** 方案资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(ReviewChannelCostVersionRequest request) {
        return LockCommand.lock(BillingLocks.channelCostPlan(request.getPlanCode()));
    }

    @Override
    protected ChannelCostVersionView processWithinLock(ReviewChannelCostVersionRequest request) {
        ChannelCostVersionContext context =
            channelCostPlanService.loadVersion(request.getPlanCode(), request.getCostVer());
        ChannelCostVersionReviewValidator.checkReviewable(request.getPlanCode(), context.getVer(),
            request.getCheckerId(), request.getApprovedContentHash());
        channelCostPlanService.rejectVersion(context, request.getCheckerId());
        return context.toView();
    }
}
