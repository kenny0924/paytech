package com.paytech.payment.execution.integration;

import java.util.List;

import com.paytech.payment.execution.domain.ChannelExecutionCommand;
import com.paytech.payment.execution.domain.ChannelExecutionResult;
import com.paytech.payment.routing.domain.RouteCandidate;
import com.paytech.payment.routing.domain.RouteContext;

/**
 * Execution 使用的 Channel 能力端口；Channel 是独立能力模块，由 Web 组合根实现。
 * <li>两个方法都在数据库事务外调用。</li>
 */
public interface PaymentChannelClient {
    /**
     * 查询 Intent 冻结 OE 内的 MID 候选
     *
     * @param context
     *            路由上下文
     * @return 按候选查询顺序排列的候选，可为空
     */
    List<RouteCandidate> queryRouteCandidates(RouteContext context);

    /**
     * 按冻结坐标经 Platform 执行一次渠道动作
     * <li>冻结坐标不可用或 Platform 保证未发出请求时为 NOT_SENT；其他失败为 ERROR，不自动重试。</li>
     *
     * @param command
     *            渠道动作
     * @return 执行结论
     */
    ChannelExecutionResult execute(ChannelExecutionCommand command);
}
