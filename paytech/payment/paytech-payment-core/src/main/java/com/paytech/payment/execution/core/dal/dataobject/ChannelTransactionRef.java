package com.paytech.payment.execution.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.payment.execution.core.action.ActionType;

import lombok.Getter;
import lombok.Setter;

/** 不可变渠道资金交易引用 */
@Getter
@Setter
public class ChannelTransactionRef {
    /** 渠道交易引用 ID */
    private Long channelTransactionRefId;

    /** 原业务动作 ID */
    private Long paymentActionId;

    /** 渠道 ID */
    private Long channelId;

    /** MID ID */
    private Long midId;

    /** 资金动作类型 */
    private ActionType referenceType;

    /** 渠道返回的真实交易编号 */
    private String externalTransactionId;

    /** 原成功交易引用 ID */
    private Long originalChannelTxnRefId;

    /** 渠道提供的交易时间 */
    private Instant channelEventTime;

    /** 交易金额 */
    private BigDecimal amount;

    /** 交易币种 */
    private String currency;

    /** 记录创建时间 */
    private Instant createTime;

}
