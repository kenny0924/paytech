package com.paytech.reconciliation.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.reconciliation.api.enums.ClearingDirection;
import com.paytech.reconciliation.api.enums.PlatformTxnSource;
import com.paytech.reconciliation.api.enums.PlatformTxnSourceRefType;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;

import lombok.Getter;
import lombok.Setter;

/**
 * t_platform_txn（平台流水）
 * <li>平台流水（内部推送）：由 billing 事实生成的逐笔渠道待清算应收或应付，等待渠道账单行逐笔覆盖；只记进度，不记余额。</li>
 */
@Getter
@Setter
public class PlatformTxn {
    /**
     * 平台流水ID
     * <li>Snowflake 主键。</li>
     */
    private Long id;

    /**
     * 清算账户ID
     * <li>所属清算账户，取自 billing 事实引用 CLEARING_ACCOUNT。</li>
     */
    private Long clearingAccountId;

    /**
     * 方向
     * <li>渠道欠平台为应收，平台欠渠道为应付。允许值：RECEIVABLE、PAYABLE。</li>
     */
    private ClearingDirection direction;

    /**
     * 流水来源
     * <li>来源 billing 事实类型；切片 1 只产生 PAYMENT、REFUND。允许值：PAYMENT、DUPLICATE_CAPTURE、REFUND、DUPLICATE_COMPENSATION、CHARGEBACK、CHARGEBACK_REVERSAL。</li>
     */
    private PlatformTxnSource txnSource;

    /**
     * 计费事实ID
     * <li>来源 billing 事实 ID，消费幂等键。</li>
     */
    private Long billingFactId;

    /**
     * 经营主体编号
     * <li>事实所属经营主体。</li>
     */
    private String entityNo;

    /**
     * 来源对象类型
     * <li>来源业务对象类型。允许值：PAYMENT_ATTEMPT、CHANNEL_REFUND、DUPLICATE_ITEM、DISPUTE_NOTICE。</li>
     */
    private PlatformTxnSourceRefType sourceRefType;

    /**
     * 来源对象ID
     * <li>来源业务对象 ID（十进制字符串），不建跨库外键。</li>
     */
    private String sourceRefId;

    /**
     * 渠道交易引用ID
     * <li>Payment 渠道交易引用的内部 ID，取自事实引用 CHANNEL_TRANSACTION_REF。</li>
     */
    private String channelTransactionRefId;

    /**
     * 外部交易号类型
     * <li>渠道外部交易号的类型，与渠道账单行的 reference_type 对应。取值来源：Payment 渠道交易引用类型目录；非固定枚举。</li>
     */
    private String referenceType;

    /**
     * 外部交易号
     * <li>渠道外部交易号，匹配主键之一。</li>
     */
    private String externalTransactionId;

    /**
     * 币种
     * <li>币种。取值来源：ISO 4217 币种代码；非固定枚举。</li>
     */
    private String currency;

    /**
     * 预期金额
     * <li>预期由渠道清算的金额，取事实 grossAmount；正数。</li>
     */
    private BigDecimal expectedAmount;

    /**
     * 预估渠道费
     * <li>billing 预估的渠道费，取事实 CHANNEL_COST 行合计；无则为 0。</li>
     */
    private BigDecimal expectedFee;

    /**
     * 已匹配金额
     * <li>已被渠道账单行覆盖的金额，不超过 expected_amount。</li>
     */
    private BigDecimal matchedAmount;

    /**
     * 实际渠道费
     * <li>已匹配账单行确认的实际渠道费累计。</li>
     */
    private BigDecimal actualFee;

    /**
     * 已结清金额
     * <li>已随账单对平登记或经归类处置结清的金额，不超过 matched_amount 与归类金额之和。</li>
     */
    private BigDecimal settledAmount;

    /**
     * 状态
     * <li>逐笔对账进度。允许值：OPEN、PARTIALLY_MATCHED、MATCHED、SETTLED、EXCEPTION。</li>
     */
    private PlatformTxnStatus status;

    /**
     * 业务时间
     * <li>来源事实的业务发生 UTC 时间，用于账龄与查询。</li>
     */
    private Instant businessTime;

    /**
     * 乐观锁版本
     * <li>进度与状态更新的乐观锁版本。</li>
     */
    private Long version;

    /**
     * 创建时间
     * <li>登记的 UTC 微秒时间。</li>
     */
    private Instant createTime;

    /**
     * 更新时间
     * <li>最后更新的 UTC 微秒时间。</li>
     */
    private Instant updateTime;
}
