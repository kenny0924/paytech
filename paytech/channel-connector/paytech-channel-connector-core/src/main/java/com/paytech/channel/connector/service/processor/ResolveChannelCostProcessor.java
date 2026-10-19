package com.paytech.channel.connector.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.enums.ChannelCostErrorCode;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostComponent;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlan;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostPlanVer;
import com.paytech.channel.connector.core.dal.dataobject.ChannelCostScope;
import com.paytech.channel.connector.core.enums.ChannelCostPlanStatus;
import com.paytech.channel.connector.domain.ChannelCostResolutionView;
import com.paytech.channel.connector.domainservice.repository.ChannelCostComponentRepository;
import com.paytech.channel.connector.domainservice.repository.ChannelCostPlanRepository;
import com.paytech.channel.connector.domainservice.repository.ChannelCostPlanVerRepository;
import com.paytech.channel.connector.domainservice.repository.ChannelCostScopeRepository;
import com.paytech.channel.connector.service.processor.request.ResolveChannelCostRequest;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 渠道成本命中（只读、幂等、无事务）
 * <li>按 Channel 取唯一方案且必须 ACTIVE → 取 status ∈ {ACTIVE, INACTIVE} 且 effective_from ≤ asOfTime &lt; effective_to（为空视为无穷）
 * 的唯一版本 → 六个维度精确命中唯一范围 → 返回该范围按 componentCode 升序的全部组成。</li>
 * <li>任一步缺失返回 400 CHANNEL_COST_NOT_RESOLVED，覆盖版本多于一个返回 400 CHANNEL_COST_AMBIGUOUS，消息含全部请求维度；
 * 范围由 (版本, 六维) 唯一键保证至多一个。不计算金额，不返回空组成：已命中范围没有组成属于数据不变量破坏。</li>
 * <li>多次读取观察相邻已提交状态即可：版本与范围、组成写入后不可变，指针切换不影响按时间区间命中。</li>
 */
@Component
@AllArgsConstructor
public class ResolveChannelCostProcessor
    extends AbstractProcessor<ResolveChannelCostRequest, ChannelCostResolutionView> {
    private final ChannelCostPlanRepository channelCostPlanRepository;
    private final ChannelCostScopeRepository channelCostScopeRepository;
    private final ChannelCostPlanVerRepository channelCostPlanVerRepository;
    private final ChannelCostComponentRepository channelCostComponentRepository;

    @Override
    protected ChannelCostResolutionView processRequest(ResolveChannelCostRequest request) {
        ChannelCostPlan plan = getActivePlan(request);
        ChannelCostPlanVer ver = getEffectiveVersion(plan, request);
        ChannelCostScope scope = getMatchedScope(ver, request);
        return ChannelCostResolutionView.of(plan, ver, scope, getComponents(scope));
    }

    /** Channel 的唯一方案，必须为 ACTIVE。 */
    ChannelCostPlan getActivePlan(ResolveChannelCostRequest request) {
        ChannelCostPlan plan = channelCostPlanRepository.findByChannelId(request.getChannelId());
        if (plan == null || plan.getStatus() != ChannelCostPlanStatus.ACTIVE) {
            throw new FailException(ChannelCostErrorCode.CHANNEL_COST_NOT_RESOLVED,
                "Channel 无 ACTIVE 渠道成本方案: " + request.dimensions());
        }
        return plan;
    }

    /** 覆盖命中时间的唯一 ACTIVE / INACTIVE 版本。 */
    ChannelCostPlanVer getEffectiveVersion(ChannelCostPlan plan, ResolveChannelCostRequest request) {
        List<ChannelCostPlanVer> versions = channelCostPlanVerRepository.findEffectiveAt(plan.getId(),
            request.getAsOfTime());
        if (versions.isEmpty()) {
            throw new FailException(ChannelCostErrorCode.CHANNEL_COST_NOT_RESOLVED,
                "无覆盖命中时间的渠道成本版本: 方案 " + plan.getPlanCode() + ", " + request.dimensions());
        }
        if (versions.size() > 1) {
            throw new FailException(ChannelCostErrorCode.CHANNEL_COST_AMBIGUOUS, "命中多个渠道成本版本: 方案 "
                + plan.getPlanCode() + ", 版本 " + versions.stream().map(ChannelCostPlanVer::getCostVer).toList() + ", "
                + request.dimensions());
        }
        return versions.getFirst();
    }

    /** 版本内六个维度精确命中的范围。 */
    ChannelCostScope getMatchedScope(ChannelCostPlanVer ver, ResolveChannelCostRequest request) {
        ChannelCostScope scope = channelCostScopeRepository.findMatch(ver.getId(), request.getMidId(),
            request.getEntityNo(), request.getCountryCode(), request.getCurrency(), request.getPaymentMethodCode(),
            request.getActionType());
        if (scope == null) {
            throw new FailException(ChannelCostErrorCode.CHANNEL_COST_NOT_RESOLVED,
                "无精确命中的渠道成本范围: 版本 " + ver.getCostVer() + ", " + request.dimensions());
        }
        return scope;
    }

    /** 命中范围的全部成本组成，按组成代码升序；为空属于数据不变量破坏。 */
    List<ChannelCostComponent> getComponents(ChannelCostScope scope) {
        List<ChannelCostComponent> components = channelCostComponentRepository.findByScope(scope.getId());
        if (components.isEmpty()) {
            throw new ErrorException("渠道成本范围没有成本组成: 范围 " + scope.getId());
        }
        return components;
    }
}
