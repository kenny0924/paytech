package com.paytech.payment.execution.domain;

import java.math.BigDecimal;

import com.paytech.channel.connector.api.enums.ChannelPaymentStatus;
import com.paytech.payment.execution.core.enums.ChannelExecutionOutcome;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * channel-connector 已映射的渠道动作结论（D06：Platform 输出键的解释与 NOT_SENT 判定都在 channel-connector）。
 * <li>redirectUrl 是临时交互指令，不得持久化或写入日志。</li>
 */
@Getter
@AllArgsConstructor
public class ChannelExecutionResult {
    /** 执行结论 */
    private final ChannelExecutionOutcome outcome;

    /** SUCCESS 时的支付状态；其余结论或无法识别时为 null */
    private final ChannelPaymentStatus paymentStatus;

    /** 待认证跳转地址；没有交互指令时为 null */
    private final String redirectUrl;

    /** 渠道给出的交互类型代码（THREE_DS_CHALLENGE、REDIRECT）；没有交互指令时为 null */
    private final String nextActionType;

    /** 渠道交易编号 */
    private final String channelTransactionRef;

    /** 渠道原始状态 */
    private final String channelStatus;

    /** SUCCESS/FAIL 为渠道失败代码；NOT_SENT 为未发出原因；其余为 null */
    private final String failureCode;

    /** 渠道失败说明 */
    private final String failureMessage;

    /** 查单时渠道记录的金额（主单位）；其他操作或未返回时为 null */
    private final BigDecimal amount;

    /** 查单时渠道记录的币种；其他操作或未返回时为 null */
    private final String currency;

    /** 结果未知（channel-connector 不可达、超时或返回无法识别的结论）。 */
    public static ChannelExecutionResult unknown() {
        return new ChannelExecutionResult(ChannelExecutionOutcome.ERROR, null, null, null, null, null, null, null, null,
            null);
    }

    @Override
    public String toString() {
        return "ChannelExecutionResult[" + outcome + ", " + paymentStatus + ", values redacted]";
    }
}
