package com.paytech.payment.routing.service;

import com.paytech.payment.routing.domain.RouteContext;
import com.paytech.payment.routing.domain.RouteResult;

/**
 * 支付路由能力
 */
public interface RouteService {
    /**
     * 选择支付渠道
     * <li>在指定 OE 内返回完整渠道、账户和 MID；无法选路时抛出异常。</li>
     * <li>仅计算选择结果，不保存 Attempt 或发送支付。</li>
     * 
     * @param context
     *            路由上下文
     * @return 路由结果
     */
    RouteResult route(RouteContext context);
}
