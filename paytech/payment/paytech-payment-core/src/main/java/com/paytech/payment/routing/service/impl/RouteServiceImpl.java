package com.paytech.payment.routing.service.impl;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.paytech.channel.connector.api.ChannelCapabilityCodes;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.payment.execution.core.action.ActionChannelOperations;
import com.paytech.payment.execution.core.action.ActionType;
import com.paytech.payment.execution.core.enums.CaptureMode;
import com.paytech.payment.routing.domain.RouteCandidate;
import com.paytech.payment.routing.domain.RouteContext;
import com.paytech.payment.routing.domain.RouteResult;
import com.paytech.payment.routing.service.RouteService;

/**
 * 最小真实路由（PayerMax 卡直付对接技术方案第 6 节）：按捕获模式要求的动作能力与金额上下限筛选，再按 3DS 能力优先排序后取第一个。
 * <li>3DS 全链路（technique 3.2，D2）：路由在风控之前，不知道本次是否要求 3DS，因此不论是否要求都优先 SUPPORTED，其次 REQUIRED、
 * 未声明、UNSUPPORTED；同级保持候选查询顺序（稳定排序）。</li>
 * <li>不做健康度排序或路由策略。</li>
 */
@Service
public class RouteServiceImpl implements RouteService {

    /** 3DS 模式的优先次序：SUPPORTED、REQUIRED、未声明、UNSUPPORTED。 */
    static final List<String> THREE_DS_PREFERENCE = List.of(ChannelCapabilityCodes.THREE_DS_SUPPORTED,
        ChannelCapabilityCodes.THREE_DS_REQUIRED);

    /** 在明确支持本次动作且金额在上下限之内的候选中，按 3DS 优先次序选择第一个。 */
    @Override
    public RouteResult route(RouteContext context, List<RouteCandidate> candidates) {
        ActionType required = context.getCaptureMode() == CaptureMode.MANUAL ? ActionType.AUTHORIZE : ActionType.PAY;
        String action = ActionChannelOperations.of(required).getCode();
        return candidates.stream().filter(candidate -> eligible(candidate, action, context))
            .sorted(Comparator.comparingInt(RouteServiceImpl::threeDsRank)).findFirst()
            .map(RouteServiceImpl::result).orElseThrow(() -> new BizCheckFailException(
                "没有可用的渠道 MID: " + context.getEntityNo() + ", 支付方式: " + context.getPaymentMethodCode()));
    }

    /** 3DS 排序值：SUPPORTED 0、REQUIRED 1、未声明 2、UNSUPPORTED 及其他值 3。 */
    static int threeDsRank(RouteCandidate candidate) {
        if (candidate.getThreeDsMode() == null) {
            return THREE_DS_PREFERENCE.size();
        }
        int rank = THREE_DS_PREFERENCE.indexOf(candidate.getThreeDsMode());
        return rank >= 0 ? rank : THREE_DS_PREFERENCE.size() + 1;
    }

    /** 支持动作且金额不低于下限、不高于上限；未声明的限制不约束。 */
    static boolean eligible(RouteCandidate candidate, String action, RouteContext context) {
        var amount = context.getAmount().getAmount();
        return candidate.getSupportedActions() != null && candidate.getSupportedActions().contains(action)
            && (candidate.getMinAmount() == null || amount.compareTo(candidate.getMinAmount()) >= 0)
            && (candidate.getMaxAmount() == null || amount.compareTo(candidate.getMaxAmount()) <= 0);
    }

    /** 候选三元组即路由结果。 */
    static RouteResult result(RouteCandidate candidate) {
        var result = new RouteResult();
        result.setChannelId(candidate.getChannelId());
        result.setChannelAccountId(candidate.getChannelAccountId());
        result.setMidId(candidate.getMidId());
        result.setChannelCode(candidate.getChannelCode());
        result.setThreeDsMode(candidate.getThreeDsMode());
        return result;
    }
}
