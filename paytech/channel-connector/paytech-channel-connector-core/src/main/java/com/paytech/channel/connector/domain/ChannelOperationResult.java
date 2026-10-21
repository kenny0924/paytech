package com.paytech.channel.connector.domain;

import java.util.List;

import com.paytech.channel.connector.api.enums.ChannelOperationOutcome;
import com.paytech.channel.connector.api.enums.ChannelPaymentStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次渠道操作的已映射结论：执行结论加上从标准 API 输出解释出的业务字段；不含卡值或账号材料（D06）。
 * <li>SUCCESS、FAIL 的业务字段取自标准 API 输出；NOT_SENT 只有原因代码；ERROR 全部为空。</li>
 * <li>redirectUrl 是临时交互指令，调用方不得持久化。</li>
 */
@Getter
@AllArgsConstructor
public class ChannelOperationResult {
    /** 执行结论。 */
    private final ChannelOperationOutcome outcome;

    /** SUCCESS 时的支付状态；输出缺失或取值不认识时为 null。 */
    private final ChannelPaymentStatus paymentStatus;

    /** 待认证跳转地址。 */
    private final String redirectUrl;

    /** 渠道交易编号。 */
    private final String channelTransactionRef;

    /** 渠道原始状态。 */
    private final String channelStatus;

    /** SUCCESS/FAIL 为渠道失败代码；NOT_SENT 为未发出原因（protocol 未发出代码或本地校验）；ERROR 为 null。 */
    private final String failureCode;

    /** 渠道失败说明。 */
    private final String failureMessage;

    /** 标准 API 输出的顶层字段名，升序，不含值。 */
    private final List<String> rawOutputKeys;

    /** 构造执行前失败的结论。 */
    public static ChannelOperationResult notSent(String failureCode) {
        return new ChannelOperationResult(ChannelOperationOutcome.NOT_SENT, null, null, null, null, failureCode, null,
            List.of());
    }

    /** 构造结果未知的结论。 */
    public static ChannelOperationResult unknown() {
        return new ChannelOperationResult(ChannelOperationOutcome.ERROR, null, null, null, null, null, null, List.of());
    }

    /** 渠道已受理（SUCCESS）或明确拒绝（FAIL）：按 PAY 输出合同解释业务字段。 */
    public static ChannelOperationResult answered(ChannelOperationOutcome outcome, PayOutputs outputs) {
        return new ChannelOperationResult(outcome, outputs.getPaymentStatus(), outputs.getRedirectUrl(),
            outputs.getChannelTransactionRef(), outputs.getChannelStatus(), outputs.getFailureCode(),
            outputs.getFailureMessage(), outputs.keys());
    }

    @Override
    public String toString() {
        return "ChannelOperationResult[" + outcome + ", " + paymentStatus + ", values redacted]";
    }
}
