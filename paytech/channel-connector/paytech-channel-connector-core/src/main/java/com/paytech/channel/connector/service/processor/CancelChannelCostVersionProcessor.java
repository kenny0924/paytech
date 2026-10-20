package com.paytech.channel.connector.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.core.constant.ChannelConnectorLocks;
import com.paytech.channel.connector.core.dal.dataobject.ChannelConfigChange;
import com.paytech.channel.connector.core.enums.ChangeTargetType;
import com.paytech.channel.connector.core.enums.ChangeType;
import com.paytech.channel.connector.core.enums.ChannelCostVersionStatus;
import com.paytech.channel.connector.domain.ChannelCostVersionContext;
import com.paytech.channel.connector.domain.ChannelCostVersionView;
import com.paytech.channel.connector.domainservice.service.ChannelCostPlanService;
import com.paytech.channel.connector.service.processor.request.CancelChannelCostVersionRequest;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 作废未到生效时间的已批准渠道成本版本：APPROVED → INACTIVE，失效时间等于生效时间
 * <li>只有 APPROVED 且 effective_from 晚于当前时间的版本可作废；ACTIVE、其他状态或已到生效时间均业务拒绝，提示含方案代码、
 * 版本号与当前状态。作废后版本为空区间，命中接口与到期切换 Job 均不再选中它；方案当前版本指针不指向 APPROVED 版本，不需改动。</li>
 * <li>与同一方案的其他写入口共用资源锁并持续等待；本地事务内先锁方案再锁版本，以 status = APPROVED 且 effective_from 晚于当前时间
 * 条件更新（行数必须为 1），并在同一事务写入 CHANNEL_COST_VERSION / CANCEL 变更记录。无外部调用。</li>
 */
@Component
@AllArgsConstructor
public class CancelChannelCostVersionProcessor
    extends AbstractLockProcessor<CancelChannelCostVersionRequest, ChannelCostVersionView> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final ChannelCostPlanService channelCostPlanService;

    /** 方案资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(CancelChannelCostVersionRequest request) {
        return LockCommand.lock(ChannelConnectorLocks.channelCostPlan(request.getPlanCode()));
    }

    @Override
    protected ChannelCostVersionView processWithinLock(CancelChannelCostVersionRequest request) {
        // 业务验证
        ChannelCostVersionContext context =
            channelCostPlanService.loadVersion(request.getPlanCode(), request.getCostVer());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        context.checkCancellableAt(now);

        // 本地保存
        channelCostPlanService.cancelVersion(context, buildChange(context, request, now));
        return context.toView();
    }

    /** 构建 CHANNEL_COST_VERSION / CANCEL 变更记录，创建时间即作废时刻。 */
    ChannelConfigChange buildChange(ChannelCostVersionContext context, CancelChannelCostVersionRequest request,
        Instant now) {
        ChannelConfigChange change = new ChannelConfigChange();
        change.setId(idGenerator.nextId());
        change.setTargetType(ChangeTargetType.CHANNEL_COST_VERSION);
        change.setTargetId(context.getVer().getId());
        change.setChangeType(ChangeType.CANCEL);
        change.setFromStatus(ChannelCostVersionStatus.APPROVED.getCode());
        change.setToStatus(ChannelCostVersionStatus.INACTIVE.getCode());
        change.setOperatorId(request.getOperatorId());
        change.setReason(request.getReason());
        change.setCreateTime(now);
        return change;
    }
}
