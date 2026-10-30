package com.paytech.operations.console.bff.test.simulator;

/**
 * payout 收款账户与余额解释的 Loopback 测试端点
 * <li>按“方法 + 路径”返回配置的 payout 合同 DTO，并记录每次调用；未配置的请求返回 500 并计入 unexpected。</li>
 *
 * @author Claude
 */
public final class PayoutOwnerSimulator extends FinanceOwnerSimulator {

    /** 启动随机 loopback 端口。 */
    public PayoutOwnerSimulator() {
        super("payout");
    }
}
