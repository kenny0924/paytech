package com.paytech.operations.console.bff.test.simulator;

/**
 * tax 订单查询接口的 Loopback 测试端点
 * <li>按“方法 + 路径”返回测试配置的 tax 合同 DTO（GET /tax/v1/orders/{orderNo}）；未配置的调用计入 unexpected。</li>
 */
public final class TaxOwnerSimulator extends FinanceOwnerSimulator {

    /** 启动随机 loopback 端口。 */
    public TaxOwnerSimulator() {
        super("tax");
    }
}
