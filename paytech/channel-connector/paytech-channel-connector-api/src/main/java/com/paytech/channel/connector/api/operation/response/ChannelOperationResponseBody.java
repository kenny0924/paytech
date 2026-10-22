package com.paytech.channel.connector.api.operation.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 一次渠道操作的已映射结论（D06：Platform 输出键与 NOT_SENT Problem 代码只在 channel-connector 解释）。
 * <li>outcome：SUCCESS 渠道已受理（业务状态见 paymentStatus）；FAIL 渠道明确拒绝；ERROR 结果未知；NOT_SENT 保证未发出渠道请求。</li>
 * <li>redirectUrl 是临时交互指令，调用方不得持久化或写入日志。</li>
 * <li>QUERY（原交易查单）另返回渠道记录的金额与币种；渠道无此交易时 outcome=FAIL、paymentStatus=NOT_FOUND。</li>
 */
@Getter
@Setter
public class ChannelOperationResponseBody {
    /** 执行结论代码，取值见 ChannelOperationOutcome。 */
    private String outcome;

    /** SUCCESS 时的支付状态代码（ChannelPaymentStatus）；QUERY 无此交易时 FAIL + NOT_FOUND；其余结论或输出无法识别时为空。 */
    private String paymentStatus;

    /** 待认证时的跳转地址；没有交互指令时为空。 */
    private String redirectUrl;

    /**
     * PAY 待交互时渠道给出的交互类型
     * <li>取值 THREE_DS_CHALLENGE、REDIRECT；有跳转地址但渠道未给类型或类型不认识时为 REDIRECT；没有跳转地址时为空。</li>
     */
    private String nextActionType;

    /** QUERY 时渠道记录的金额，交易币种主单位十进制字符串；渠道未返回或其他操作为空。 */
    private String amount;

    /** QUERY 时渠道记录的币种；渠道未返回或其他操作为空。 */
    private String currency;

    /** 渠道交易编号；渠道未返回时为空。 */
    private String channelTransactionRef;

    /** 渠道原始状态；渠道未返回时为空。 */
    private String channelStatus;

    /** SUCCESS/FAIL 时为渠道失败代码；NOT_SENT 时为未发出原因（Platform Problem code 或本地校验代码）；其余为空。 */
    private String failureCode;

    /** 渠道失败说明；渠道未返回时为空。 */
    private String failureMessage;

    /** Platform 输出中出现的顶层字段名（升序，供排障，不含任何值）。 */
    private List<String> rawOutputKeys;

    @Override
    public String toString() {
        return "ChannelOperationResponseBody[" + outcome + ", " + paymentStatus + ", values redacted]";
    }
}
