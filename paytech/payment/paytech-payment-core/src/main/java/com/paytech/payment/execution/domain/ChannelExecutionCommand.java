package com.paytech.payment.execution.domain;

import java.math.BigDecimal;
import java.util.Map;

import com.paytech.channel.connector.api.enums.ChannelOperation;
import com.paytech.channel.connector.api.enums.ExecutionPurpose;

import lombok.Getter;
import lombok.Setter;

/**
 * Execution 发起的一次渠道动作：冻结的 OE、Channel、Account、MID 与新支付维度，加上标准 API 业务输入。
 * <li>输入不含卡值或账号材料；卡只以读卡票据出现。标准 API 编码由 channel-connector 按渠道操作确定。</li>
 * <li>新支付（NEW_PAYMENT）携带交易维度与金额；原交易查单（ORIGINAL_TRANSACTION）只有冻结坐标与输入。</li>
 */
@Getter
@Setter
public class ChannelExecutionCommand {
    /** 渠道操作，例如 PAY、AUTHORIZE、QUERY */
    private ChannelOperation operation;

    /** 执行用途：新支付或原交易后续操作（查单） */
    private ExecutionPurpose purpose;

    /** 冻结 OE */
    private String entityNo;

    /** 冻结渠道 ID */
    private Long channelId;

    /** 冻结渠道账号 ID */
    private Long channelAccountId;

    /** 冻结 MID ID */
    private Long midId;

    /** 支付方式代码 */
    private String paymentMethodCode;

    /** 消费者国家 */
    private String countryCode;

    /** 交易币种 */
    private String currency;

    /** 商户类别码 */
    private String mccCode;

    /** 卡组织（支付体系）代码：Token 冻结的 BIN 库卡组织，与选路时一致；未识别或非卡支付为空 */
    private String schemeCode;

    /** 交易金额（主单位） */
    private BigDecimal amount;

    /** 标准 API 业务输入，不含 channelAccount */
    private Map<String, Object> input;

    @Override
    public String toString() {
        return "ChannelExecutionCommand[" + operation + ", input redacted]";
    }
}
