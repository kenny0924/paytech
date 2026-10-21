package com.paytech.reconciliation.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.reconciliation.api.enums.ClearingDirection;
import com.paytech.reconciliation.api.enums.PlatformTxnSource;
import com.paytech.reconciliation.api.enums.PlatformTxnSourceRefType;

import lombok.Builder;
import lombok.Getter;

/**
 * 由 billing BillingFactRecorded 派生的平台流水事实（technique D-06）
 * <li>只来自已批准、无更正关系的 PAYMENT（应收）与 REFUND（应付）事实；引用缺失的事实不会走到这里。</li>
 */
@Getter
@Builder
public class PlatformTxnFact {
    /** 来源 billing 事实主键（消费幂等键） */
    private final Long billingFactId;

    /** 事实引用 CLEARING_ACCOUNT 的清算账户主键 */
    private final Long clearingAccountId;

    /** PAYMENT 为 RECEIVABLE，REFUND 为 PAYABLE */
    private final ClearingDirection direction;

    /** 来源事实类型 */
    private final PlatformTxnSource txnSource;

    /** 事实经营主体 */
    private final String entityNo;

    /** 来源业务对象类型：PAYMENT_ATTEMPT 或 CHANNEL_REFUND */
    private final PlatformTxnSourceRefType sourceRefType;

    /** 来源业务对象 ID */
    private final String sourceRefId;

    /** 引用 CHANNEL_TRANSACTION_REF 的 refId */
    private final String channelTransactionRefId;

    /** 引用 CHANNEL_TRANSACTION_REF 的外部交易号类型 */
    private final String referenceType;

    /** 引用 CHANNEL_TRANSACTION_REF 的外部交易号 */
    private final String externalTransactionId;

    /** 币种 */
    private final String currency;

    /** 事实 grossAmount */
    private final BigDecimal expectedAmount;

    /** CHANNEL_COST 组件行 postedAmount 合计；没有为 0 */
    private final BigDecimal expectedFee;

    /** 事实业务发生时间 */
    private final Instant businessTime;
}
