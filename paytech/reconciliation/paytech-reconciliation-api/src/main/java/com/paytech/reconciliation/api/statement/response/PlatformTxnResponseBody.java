package com.paytech.reconciliation.api.statement.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 平台流水：由 billing 事实生成的逐笔渠道待清算应收或应付及其对账进度。 */
@Getter
@Setter
public class PlatformTxnResponseBody {
    /** 平台流水主键 */
    private String platformTxnId;

    /** 清算账户主键 */
    private String clearingAccountId;

    /** 方向：RECEIVABLE、PAYABLE */
    private String direction;

    /** 来源 billing 事实类型：PAYMENT、REFUND 等 */
    private String txnSource;

    /** 来源 billing 事实主键 */
    private String billingFactId;

    /** 经营主体编号 */
    private String entityNo;

    /** 来源业务对象类型：PAYMENT_ATTEMPT、CHANNEL_REFUND 等 */
    private String sourceRefType;

    /** 来源业务对象 ID */
    private String sourceRefId;

    /** Payment 渠道交易引用内部 ID */
    private String channelTransactionRefId;

    /** 渠道外部交易号类型 */
    private String referenceType;

    /** 渠道外部交易号 */
    private String externalTransactionId;

    /** 币种 */
    private String currency;

    /** 预期由渠道清算的金额，按币种精度补齐 */
    private String expectedAmount;

    /** billing 预估渠道费，按币种精度补齐 */
    private String expectedFee;

    /** 已被渠道账单行覆盖的金额，按币种精度补齐 */
    private String matchedAmount;

    /** 已匹配账单行确认的实际渠道费累计，按币种精度补齐 */
    private String actualFee;

    /** 已随账单对平登记或经归类结清的金额，按币种精度补齐 */
    private String settledAmount;

    /** 状态：OPEN、PARTIALLY_MATCHED、MATCHED、SETTLED、EXCEPTION */
    private String status;

    /** 来源事实业务发生的 UTC 时间 */
    private Instant businessTime;

    /** 乐观锁版本 */
    private String version;

    /** 登记的 UTC 时间 */
    private Instant createTime;

    /** 最后更新的 UTC 时间 */
    private Instant updateTime;
}
