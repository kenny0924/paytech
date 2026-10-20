package com.paytech.channel.connector.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.core.constant.ChannelConnectorLocks;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlan;
import com.paytech.channel.connector.core.enums.ChannelCostPlanStatus;
import com.paytech.channel.connector.domain.ChannelCostPlanView;
import com.paytech.channel.connector.domainservice.repository.ChannelCostPlanRepository;
import com.paytech.channel.connector.domainservice.repository.PaymentChannelRepository;
import com.paytech.channel.connector.domainservice.service.ChannelCostPlanService;
import com.paytech.channel.connector.service.processor.request.CreateChannelCostPlanRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;

import lombok.AllArgsConstructor;

/**
 * 创建 DRAFT 渠道成本方案，尚无当前版本
 * <li>Channel 必须存在于本库 t_payment_channel，否则业务拒绝。</li>
 * <li>每个 Channel 只有一个方案，方案代码全局唯一；任一已存在时业务拒绝。</li>
 * <li>与同一方案代码的版本写入口共用资源锁并持续等待；Channel 与代码唯一键是最终防线，并发重复按 409 拒绝。</li>
 */
@Component
@AllArgsConstructor
public class CreateChannelCostPlanProcessor
    extends AbstractLockProcessor<CreateChannelCostPlanRequest, ChannelCostPlanView> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final ChannelCostPlanService channelCostPlanService;
    private final PaymentChannelRepository paymentChannelRepository;
    private final ChannelCostPlanRepository channelCostPlanRepository;

    /** 方案资源锁，持续等待。 */
    @Override
    protected LockCommand lockCommand(CreateChannelCostPlanRequest request) {
        return LockCommand.lock(ChannelConnectorLocks.channelCostPlan(request.getPlanCode()));
    }

    @Override
    protected ChannelCostPlanView processWithinLock(CreateChannelCostPlanRequest request) {
        // 业务验证
        checkChannelExists(request);
        checkChannelWithoutPlan(request);
        checkPlanCodeUnused(request);

        // 本地保存
        ChannelCostPlan plan = buildPlan(request);
        channelCostPlanService.createPlan(plan);
        return ChannelCostPlanView.of(plan, List.of());
    }

    /** Channel 必须存在于本库。 */
    void checkChannelExists(CreateChannelCostPlanRequest request) {
        if (paymentChannelRepository.findById(request.getChannelId()) == null) {
            throw new BizCheckFailException("渠道不存在: " + request.getChannelId());
        }
    }

    /** 每个 Channel 只能有一个成本方案。 */
    void checkChannelWithoutPlan(CreateChannelCostPlanRequest request) {
        ChannelCostPlan existing = channelCostPlanRepository.findByChannelId(request.getChannelId());
        if (existing != null) {
            throw new BizCheckFailException("Channel 已有渠道成本方案: Channel " + request.getChannelId() + ", 方案 "
                + existing.getPlanCode());
        }
    }

    /** 方案代码不能重复。 */
    void checkPlanCodeUnused(CreateChannelCostPlanRequest request) {
        if (channelCostPlanRepository.findByCode(request.getPlanCode()) != null) {
            throw new BizCheckFailException("渠道成本方案代码已存在: " + request.getPlanCode());
        }
    }

    /** 构建 DRAFT 方案。 */
    ChannelCostPlan buildPlan(CreateChannelCostPlanRequest request) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ChannelCostPlan plan = new ChannelCostPlan();
        plan.setId(idGenerator.nextId());
        plan.setChannelId(request.getChannelId());
        plan.setPlanCode(request.getPlanCode());
        plan.setStatus(ChannelCostPlanStatus.DRAFT);
        plan.setVersion(0L);
        plan.setCreateTime(now);
        plan.setUpdateTime(now);
        return plan;
    }
}
