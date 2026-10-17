package com.paytech.billing.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.billing.core.constant.BillingLocks;
import com.paytech.billing.core.dal.dataobject.ChannelCostPlan;
import com.paytech.billing.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.billing.domain.ChannelCostVersionContext;
import com.paytech.billing.domain.view.ChannelCostVersionView;
import com.paytech.billing.domainservice.repository.ChannelCostPlanVerRepository;
import com.paytech.billing.domainservice.service.ChannelCostPlanService;
import com.paytech.billing.domainservice.validator.cost.ChannelCostVersionReviewValidator;
import com.paytech.billing.service.processor.request.ReviewChannelCostVersionRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 批准渠道成本版本：只能批准 PENDING_APPROVAL 版本，Checker ≠ Maker，确认摘要等于版本 contentHash
 * <li>批准时刻已到生效时间：版本 ACTIVE，原当前版本 INACTIVE 且失效时间等于新版本生效时间，方案当前指针原子切换并置为
 * ACTIVE；新版本生效时间必须晚于原当前版本，避免生效区间倒置。</li>
 * <li>未到生效时间：版本 APPROVED，当前指针不变（到期切换不在本期）。</li>
 * <li>与同一方案的其他写入口共用资源锁并持续等待；事务内先锁方案再锁版本，指针切换以方案版本 CAS 保护。</li>
 */
@Component
@AllArgsConstructor
public class ApproveChannelCostVersionProcessor
    extends AbstractLockProcessor<ReviewChannelCostVersionRequest, ChannelCostVersionView> {
    private final Clock clock;
    private final ChannelCostPlanService channelCostPlanService;
    private final ChannelCostPlanVerRepository channelCostPlanVerRepository;

    /** 方案资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(ReviewChannelCostVersionRequest request) {
        return LockCommand.lock(BillingLocks.channelCostPlan(request.getPlanCode()));
    }

    @Override
    protected ChannelCostVersionView processWithinLock(ReviewChannelCostVersionRequest request) {
        ChannelCostVersionContext loaded =
            channelCostPlanService.loadVersion(request.getPlanCode(), request.getCostVer());
        ChannelCostVersionReviewValidator.checkReviewable(request.getPlanCode(), loaded.getVer(),
            request.getCheckerId(), request.getApprovedContentHash());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ChannelCostPlanVer currentVer = loaded.effectiveAt(now) ? getReplacedVersion(loaded) : null;
        ChannelCostVersionContext context = new ChannelCostVersionContext(loaded.getPlan(), loaded.getVer(),
            loaded.getScopes(), loaded.getComponents(), loaded.getMinorUnits(), currentVer);
        channelCostPlanService.approveVersion(context, request.getCheckerId(), now);
        return context.toView();
    }

    /** 读取将被替代的当前版本；方案尚无当前版本时为空，新版本生效时间必须晚于它。 */
    ChannelCostPlanVer getReplacedVersion(ChannelCostVersionContext context) {
        ChannelCostPlan plan = context.getPlan();
        if (plan.getCurrentVerId() == null) {
            return null;
        }
        ChannelCostPlanVer currentVer = channelCostPlanVerRepository.findById(plan.getCurrentVerId());
        if (currentVer == null) {
            throw new ErrorException("渠道成本方案当前版本指针无对应版本: " + plan.getPlanCode() + ", " + plan.getCurrentVerId());
        }
        if (!context.getVer().getEffectiveFrom().isAfter(currentVer.getEffectiveFrom())) {
            throw new BizCheckFailException("新版本生效时间必须晚于当前版本: 方案 " + plan.getPlanCode() + ", 版本 "
                + context.getVer().getCostVer() + ", 当前版本 " + currentVer.getCostVer());
        }
        return currentVer;
    }
}
