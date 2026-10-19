package com.paytech.channel.connector.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.core.constant.ChannelConnectorLocks;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.channel.connector.domain.ChannelCostVersionContext;
import com.paytech.channel.connector.domain.ChannelCostVersionView;
import com.paytech.channel.connector.domainservice.service.ChannelCostPlanService;
import com.paytech.channel.connector.domainservice.validator.cost.ChannelCostVersionReviewValidator;
import com.paytech.channel.connector.service.processor.request.ReviewChannelCostVersionRequest;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 批准渠道成本版本：只能批准 PENDING_APPROVAL 版本，Checker ≠ Maker，确认摘要等于版本 contentHash
 * <li>批准时刻已到生效时间：版本 ACTIVE，原当前版本 INACTIVE 且失效时间等于新版本生效时间，方案当前指针原子切换并置为
 * ACTIVE；新版本生效时间必须晚于原当前版本，避免生效区间倒置。</li>
 * <li>未到生效时间：版本 APPROVED，当前指针不变，由到期切换 Job（ActivateDueChannelCostVersionsProcessor）在生效时间到达后切换。</li>
 * <li>与同一方案的其他写入口共用资源锁并持续等待；事务内先锁方案再锁版本，指针切换以方案版本 CAS 保护。</li>
 */
@Component
@AllArgsConstructor
public class ApproveChannelCostVersionProcessor
    extends AbstractLockProcessor<ReviewChannelCostVersionRequest, ChannelCostVersionView> {
    private final Clock clock;
    private final ChannelCostPlanService channelCostPlanService;

    /** 方案资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(ReviewChannelCostVersionRequest request) {
        return LockCommand.lock(ChannelConnectorLocks.channelCostPlan(request.getPlanCode()));
    }

    @Override
    protected ChannelCostVersionView processWithinLock(ReviewChannelCostVersionRequest request) {
        // 业务验证
        ChannelCostVersionContext loaded =
            channelCostPlanService.loadVersion(request.getPlanCode(), request.getCostVer());
        ChannelCostVersionReviewValidator.checkReviewable(request.getPlanCode(), loaded.getVer(),
            request.getCheckerId(), request.getApprovedContentHash());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ChannelCostPlanVer currentVer = loaded.effectiveAt(now) ? getReplacedVersion(loaded) : null;

        // 本地保存
        ChannelCostVersionContext context = loaded.replacing(currentVer);
        channelCostPlanService.approveVersion(context, request.getCheckerId(), now);
        return context.toView();
    }

    /** 读取将被替代的当前版本；方案尚无当前版本时为空，新版本生效时间必须晚于它。 */
    ChannelCostPlanVer getReplacedVersion(ChannelCostVersionContext context) {
        ChannelCostPlanVer currentVer = channelCostPlanService.findCurrentVersion(context.getPlan());
        context.checkLaterThan(currentVer);
        return currentVer;
    }
}
