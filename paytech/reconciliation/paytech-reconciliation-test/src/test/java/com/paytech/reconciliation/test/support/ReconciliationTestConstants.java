package com.paytech.reconciliation.test.support;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 对账测试的公共基线值与测试主键序列
 * <li>基线值固定，便于断言重现；测试主键从固定起点递增，只在本 JVM 内唯一，每例清库后不复用前例数据。</li>
 */
public final class ReconciliationTestConstants {
    /** 经营主体编号。 */
    public static final String ENTITY_NO = "AE001";

    /** 清算币种。 */
    public static final String CURRENCY = "USD";

    /** 渠道编码（channel-connector 渠道目录，开放代码）。 */
    public static final String CHANNEL_CODE = "NI";

    /** 支付的渠道交易引用类型（Payment t_channel_transaction_ref.reference_type；payment-api 未发布枚举，对账侧为开放代码）。 */
    public static final String PAY_REFERENCE_TYPE = "PAY";

    /** 退款的渠道交易引用类型。 */
    public static final String REFUND_REFERENCE_TYPE = "REFUND";

    /** 提交人（Maker）Operations Account ID。 */
    public static final long MAKER_ID = 900_001L;

    /** 复核人（Checker）Operations Account ID，与提交人不同。 */
    public static final long CHECKER_ID = 900_002L;

    /** 操作人会话 ID。 */
    public static final String SESSION_ID = "reconciliation-test-session";

    private static final AtomicLong IDS = new AtomicLong(7_100_000_000_000L);

    /** 常量类不实例化。 */
    private ReconciliationTestConstants() {
    }

    /** 下一个测试主键；与生产 Snowflake 取值区间不重叠。 */
    public static long nextId() {
        return IDS.incrementAndGet();
    }
}
