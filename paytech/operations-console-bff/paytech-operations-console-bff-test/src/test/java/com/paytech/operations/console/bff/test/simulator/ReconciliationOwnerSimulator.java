package com.paytech.operations.console.bff.test.simulator;

/**
 * reconciliation 运营接口的 Loopback 测试端点
 * <li>按“方法 + 路径”返回边界清单 finance-reconciliation-boundary.json 中的 reconciliation 合同 DTO，并记录每次调用。</li>
 *
 * @author Claude
 */
public final class ReconciliationOwnerSimulator extends FinanceOwnerSimulator {

    /** 启动随机 loopback 端口。 */
    public ReconciliationOwnerSimulator() {
        super("reconciliation");
    }
}
