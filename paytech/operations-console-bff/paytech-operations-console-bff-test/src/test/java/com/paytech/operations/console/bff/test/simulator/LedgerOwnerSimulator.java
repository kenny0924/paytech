package com.paytech.operations.console.bff.test.simulator;

/**
 * ledger 账务基础资料、账本、人工调整、期间关账与记账规则接口的 Loopback 测试端点
 *
 * @author Claude
 */
public final class LedgerOwnerSimulator extends FinanceOwnerSimulator {

    /** 启动随机 loopback 端口。 */
    public LedgerOwnerSimulator() {
        super("ledger");
    }
}
