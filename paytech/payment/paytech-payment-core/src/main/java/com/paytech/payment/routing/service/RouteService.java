package com.paytech.payment.routing.service;

import java.util.List;

import com.paytech.payment.routing.domain.RouteCandidate;
import com.paytech.payment.routing.domain.RouteContext;
import com.paytech.payment.routing.domain.RouteResult;

/**
 * 支付路由能力
 */
public interface RouteService {
    /**
     * 在候选中选择支付渠道
     * <li>候选由调用方在 Intent 冻结的 OE 内查询；本方法只做选择，不读取配置、不保存 Attempt 或发送支付。</li>
     * <li>没有满足动作与金额能力的候选时抛出业务异常，不回退到其他 OE。</li>
     *
     * @param context
     *            路由上下文
     * @param candidates
     *            按候选查询顺序排列的 MID 候选
     * @return 路由结果
     */
    RouteResult route(RouteContext context, List<RouteCandidate> candidates);
}
