package com.paytech.operations.console.bff.test.simulator;

/**
 * billing 计费事实接口的 Loopback 测试端点
 * <li>按“方法 + 路径”返回边界清单中的 billing 合同 DTO（finance-adjustment-boundary.json 中 ownerPath 为 /billing/v1/facts 的行）。</li>
 *
 * @author Claude
 */
public final class BillingOwnerSimulator extends FinanceOwnerSimulator {

    /** 启动随机 loopback 端口。 */
    public BillingOwnerSimulator() {
        super("billing");
    }
}
