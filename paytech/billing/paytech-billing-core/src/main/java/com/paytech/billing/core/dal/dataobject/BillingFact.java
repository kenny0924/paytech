package com.paytech.billing.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactOriginType;

import lombok.Getter;
import lombok.Setter;

/**
 * t_billing_fact：计费事实头
 * <li>内容登记后不可修改；只有登记状态、版本定稿、复核信息、业务时间（人工登记批准时覆盖为批准时间）与发布时间随 Maker-Checker
 * 和事件发布推进。</li>
 */
@Getter
@Setter
public class BillingFact {
    /**
     * 计费事实 Snowflake 主键
     * <li>BillingFactRecorded 的聚合 ID。</li>
     */
    private Long id;

    /** 事实类型 */
    private BillingFactType factType;

    /**
     * 来源方式
     * <li>AUTO 登记即 APPROVED；MANUAL 先 DRAFT，经 Checker 批准。</li>
     */
    private FactOriginType originType;

    /**
     * 来源系统代码
     * <li>取值来源：Code-MoR 系统目录；非固定枚举。</li>
     */
    private String sourceSystem;

    /**
     * 来源事件键或命令业务键
     * <li>与 sourceSystem 组成幂等键；人工登记为提交的来源引用。</li>
     */
    private String sourceEventKey;

    /**
     * 来源业务事实的版本
     * <li>命令类与人工登记固定为 1。</li>
     */
    private Long sourceVersion;

    /** 来源载荷或命令请求的 RFC 8785 SHA-256 小写十六进制 */
    private String sourcePayloadHash;

    /**
     * 派生来源事实
     * <li>退款、争议、重复扣款及其补偿、成本调整指向原支付事实；其余为空。</li>
     */
    private Long parentFactId;

    /**
     * 本事实替代的前一事实
     * <li>首次登记为空。</li>
     */
    private Long supersedesFactId;

    /**
     * CCT Client 稳定业务 ID
     * <li>资金类事实为空。</li>
     */
    private Long clientId;

    /** Operating Entity 编号 */
    private String entityNo;

    /**
     * Local Partner 稳定业务 ID
     * <li>无合作方为空，与 financeEntityId 同有同无。</li>
     */
    private Long localPartnerId;

    /**
     * 平台服务费收取方 Finance Entity
     * <li>无合作方为空。</li>
     */
    private Long financeEntityId;

    /** 本事实全部金额的大写 ISO 4217 币种 */
    private String currency;

    /** 业务事实非负总额；人工调整为明细行之和 */
    private BigDecimal grossAmount;

    /**
     * 业务发生的 UTC 微秒时间
     * <li>人工登记 DRAFT 写提交时间，批准时覆盖为批准时间；ledger 过账日期取它。</li>
     */
    private Instant businessTime;

    /**
     * 余额到期时间
     * <li>仅 PAYMENT 且已计算时非空。</li>
     */
    private Instant balanceAvailableTime;

    /** 登记状态 */
    private BillingFactStatus status;

    /**
     * 事实版本
     * <li>DRAFT 为 0；批准时定稿为 1，替代事实为被替代事实版本 + 1；事件聚合版本。</li>
     */
    private Long factVersion;

    /**
     * MANUAL 事实的提交人
     * <li>AUTO 为空。</li>
     */
    private Long makerId;

    /**
     * MANUAL 事实的批准人
     * <li>须不同于提交人；未批准或 AUTO 为空。</li>
     */
    private Long checkerId;

    /**
     * 批准（AUTO 为登记）的 UTC 微秒时间
     * <li>事件 occurredAt；DRAFT 为空。</li>
     */
    private Instant approvedTime;

    /**
     * BillingFactRecorded 首次收到 Broker 确认的 UTC 微秒时间
     * <li>未发布为空。</li>
     */
    private Instant publishedAt;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
