package com.paytech.operations.console.bff.test.simulator;

/**
 * account 余额查询与明细账查询（Owner 账户、明细流水、过账批次）接口的 Loopback 测试端点
 * <li>按“方法 + 路径”返回边界清单中的 account 合同 DTO（finance-balance-boundary.json、finance-subledger-boundary.json）。</li>
 *
 * @author Claude
 */
public final class AccountOwnerSimulator extends FinanceOwnerSimulator {

    /** 启动随机 loopback 端口。 */
    public AccountOwnerSimulator() {
        super("account");
    }
}
