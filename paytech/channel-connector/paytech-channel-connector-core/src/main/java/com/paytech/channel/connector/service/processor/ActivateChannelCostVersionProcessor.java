package com.paytech.channel.connector.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.core.constant.ChannelConnectorLocks;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.channel.connector.core.enums.ChannelCostVersionStatus;
import com.paytech.channel.connector.domain.ChannelCostVersionContext;
import com.paytech.channel.connector.domainservice.service.ChannelCostPlanService;
import com.paytech.channel.connector.service.processor.request.ActivateChannelCostVersionRequest;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 把一个已到生效时间的 APPROVED 渠道成本版本切换为当前版本（到期切换 Job 的单方案步骤）
 * <li>与同一方案的 Maker-Checker 写入口共用资源锁并持续等待；锁内重读版本，已不是 APPROVED 或尚未到生效时间时不做任何写入
 * 并返回 false，重复执行幂等。</li>
 * <li>新版本生效时间必须晚于原当前版本，否则业务拒绝且版本保持 APPROVED 等待人工处理。</li>
 * <li>短事务内先锁方案再锁版本：版本 APPROVED → ACTIVE、原当前版本 INACTIVE 且失效时间为新版本生效时间、方案指针 CAS。</li>
 */
@Component
@AllArgsConstructor
public class ActivateChannelCostVersionProcessor
    extends AbstractLockProcessor<ActivateChannelCostVersionRequest, Boolean> {
    private final ChannelCostPlanService channelCostPlanService;

    /** 方案资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(ActivateChannelCostVersionRequest request) {
        return LockCommand.lock(ChannelConnectorLocks.channelCostPlan(request.getPlanCode()));
    }

    @Override
    protected Boolean processWithinLock(ActivateChannelCostVersionRequest request) {
        // 锁内复核：已处理或未到期时直接返回
        ChannelCostVersionContext loaded =
            channelCostPlanService.loadVersion(request.getPlanCode(), request.getCostVer());
        if (loaded.getVer().getStatus() != ChannelCostVersionStatus.APPROVED || !loaded.effectiveAt(request.getAsOf())) {
            return false;
        }

        // 业务验证
        ChannelCostPlanVer currentVer = channelCostPlanService.findCurrentVersion(loaded.getPlan());
        loaded.checkLaterThan(currentVer);

        // 本地保存
        channelCostPlanService.activateVersion(loaded.replacing(currentVer));
        return true;
    }
}
