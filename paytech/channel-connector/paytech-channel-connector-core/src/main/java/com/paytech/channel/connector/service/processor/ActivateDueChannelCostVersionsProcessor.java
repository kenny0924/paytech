package com.paytech.channel.connector.service.processor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlan;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.channel.connector.domain.ChannelCostActivationResult;
import com.paytech.channel.connector.domainservice.repository.ChannelCostPlanRepository;
import com.paytech.channel.connector.domainservice.repository.ChannelCostPlanVerRepository;
import com.paytech.channel.connector.service.processor.request.ActivateChannelCostVersionRequest;
import com.paytech.channel.connector.service.processor.request.ActivateDueChannelCostVersionsRequest;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 一轮渠道成本版本到期切换（XXL-Job channelCostVersionActivation）
 * <li>读取 status = APPROVED 且 effective_from 不晚于 asOf 的版本（至多 {@value #BATCH_SIZE} 条，其余留给下一轮），按方案、生效时间、
 * 版本号升序逐个交给 ActivateChannelCostVersionProcessor：每个版本一个方案锁 + 一个短事务；同一方案多个到期版本按生效时间依次切换。</li>
 * <li>单个版本失败只记 WARN 并保留 APPROVED，不阻塞其他方案；锁内复核发现已被处理的计为跳过，重复执行幂等。</li>
 * <li>本 Processor 不开启事务，逐个调用的单方案 Processor 均在事务外执行。</li>
 */
@Component
@AllArgsConstructor
public class ActivateDueChannelCostVersionsProcessor
    extends AbstractProcessor<ActivateDueChannelCostVersionsRequest, ChannelCostActivationResult> {
    /** 每轮最多处理的到期版本数。 */
    static final int BATCH_SIZE = 200;

    private static final Logger LOG = LoggerFactory.getLogger(ActivateDueChannelCostVersionsProcessor.class);

    private final ChannelCostPlanRepository channelCostPlanRepository;
    private final ChannelCostPlanVerRepository channelCostPlanVerRepository;
    private final ActivateChannelCostVersionProcessor activateChannelCostVersionProcessor;

    @Override
    protected ChannelCostActivationResult processRequest(ActivateDueChannelCostVersionsRequest request) {
        int activated = 0;
        int skipped = 0;
        int failed = 0;
        Map<Long, String> planCodes = new HashMap<>();
        List<ChannelCostPlanVer> due = channelCostPlanVerRepository.findDueApproved(request.getAsOf(), BATCH_SIZE);
        for (ChannelCostPlanVer ver : due) {
            try {
                String planCode = planCodes.computeIfAbsent(ver.getPlanId(), this::loadPlanCode);
                if (activateChannelCostVersionProcessor.process(
                    new ActivateChannelCostVersionRequest(planCode, ver.getCostVer(), request.getAsOf()))) {
                    activated++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException failure) {
                // 单个版本失败不阻塞其他方案；它保持 APPROVED，由下一轮或人工处理。
                failed++;
                LOG.warn("渠道成本版本到期切换失败: 版本 {}, 方案 {}, {}: {}", ver.getId(), ver.getPlanId(),
                    failure.getClass().getSimpleName(), failure.getMessage());
            }
        }
        return new ChannelCostActivationResult(activated, skipped, failed);
    }

    /** 读取版本所属方案代码；方案缺失属于数据不变量破坏。 */
    String loadPlanCode(long planId) {
        ChannelCostPlan plan = channelCostPlanRepository.findById(planId);
        if (plan == null) {
            throw new ErrorException("渠道成本版本所属方案不存在: " + planId);
        }
        return plan.getPlanCode();
    }
}
