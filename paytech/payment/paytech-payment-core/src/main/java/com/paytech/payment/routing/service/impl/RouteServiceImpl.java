package com.paytech.payment.routing.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.payment.routing.domain.RouteCandidate;
import com.paytech.payment.routing.domain.RouteContext;
import com.paytech.payment.routing.domain.RouteResult;
import com.paytech.payment.routing.service.RouteService;

/**
 * 最小真实路由（PayerMax 卡直付对接技术方案第 6 节）：按捕获模式要求的动作能力与金额上下限筛选，取候选查询顺序中的第一个。
 * 不做健康度排序或路由策略。
 */
@Service
public class RouteServiceImpl implements RouteService {

    /** 选择第一个明确支持本次动作且金额在上下限之内的候选。 */
    @Override
    public RouteResult route(RouteContext context, List<RouteCandidate> candidates) {
        String action = "MANUAL".equals(context.getCaptureMode()) ? "AUTHORIZE" : "PAY";
        return candidates.stream().filter(candidate -> eligible(candidate, action, context)).findFirst()
            .map(RouteServiceImpl::result).orElseThrow(() -> new BizCheckFailException(
                "没有可用的渠道 MID: " + context.getEntityNo() + ", 支付方式: " + context.getPaymentMethodCode()));
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
        return result;
    }
}
