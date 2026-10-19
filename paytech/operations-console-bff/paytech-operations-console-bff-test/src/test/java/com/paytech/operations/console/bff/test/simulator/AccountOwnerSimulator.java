package com.paytech.operations.console.bff.test.simulator;

/**
 * account 余额查询接口的 Loopback 测试端点
 *
 * @author Claude
 */
public final class AccountOwnerSimulator extends FinanceOwnerSimulator {

    /** 启动随机 loopback 端口。 */
    public AccountOwnerSimulator() {
        super("account");
    }
}
