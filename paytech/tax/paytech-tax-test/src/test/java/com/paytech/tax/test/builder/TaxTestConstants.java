package com.paytech.tax.test.builder;

import java.time.Instant;
import java.time.LocalDate;

/**
 * tax 测试的稳定业务标识与初始化数据坐标
 * <li>测试纳税主体与准入、注册、地区包使用 1000 以上的固定 ID，与初始化 DML 的保留行（1–999）分开；清理只删除 1000 以上的行。</li>
 * <li>地区包默认复用初始化 DML 的三国演示包（ID 11 / 12 / 13），以真实规则内容验证计算。</li>
 */
public final class TaxTestConstants {
    /** 测试纳税主体（非 AE001 演示主体）。 */
    public static final long SUBJECT_PARTY_ID = 880000000101L;

    /** 没有任何准入的税务责任方（PSP Client）。 */
    public static final long UNMANAGED_PARTY_ID = 880000000909L;

    /** 买方 Client。 */
    public static final long BUYER_CLIENT_ID = 770000000001L;

    /** 收单 OE。 */
    public static final String ENTITY_NO = "AE001";

    /** 订单 ID 基数；用例按需加偏移。 */
    public static final long ORDER_ID = 660000000001L;

    /** 订单号。 */
    public static final String ORDER_NO = "ORD-TAX-0001";

    /** 美元。 */
    public static final String USD = "USD";

    /** 初始化 DML 的税类。 */
    public static final String SAAS_AUTOMATED = "SAAS_AUTOMATED";

    /** 初始化 DML 的 AE 地区包（5%，STANDARD）。 */
    public static final long SEED_POLICY_AE = 11L;

    /** 初始化 DML 的 SA 地区包（15%，NON_RESIDENT）。 */
    public static final long SEED_POLICY_SA = 12L;

    /** 初始化 DML 的 EG 地区包（14%，SVRR）。 */
    public static final long SEED_POLICY_EG = 13L;

    /** 初始化 DML 保留行的 ID 上界（不含）。 */
    public static final long RESERVED_ID_LIMIT = 1000L;

    /** 计算的候选税点日期。 */
    public static final LocalDate TAX_POINT_DATE = LocalDate.of(2026, 10, 29);

    /** 支付成功时间（UTC 2026-10-29 21:30，SA 当地已是 10-30）。 */
    public static final Instant PAYMENT_SUCCEEDED_TIME = Instant.parse("2026-10-29T21:30:00Z");

    /** 订单聚合版本。 */
    public static final long ORDER_VERSION = 5L;

    /** 纯常量。 */
    private TaxTestConstants() {
    }
}
