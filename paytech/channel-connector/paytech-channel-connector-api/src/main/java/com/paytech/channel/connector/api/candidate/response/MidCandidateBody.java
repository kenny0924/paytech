package com.paytech.channel.connector.api.candidate.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 一个启用的 MID 候选：渠道三元组与本次维度下已解析的能力；不含账号参数、Token 或密钥。 */
@Getter
@Setter
public class MidCandidateBody {
    /** 渠道 ID，十进制字符串。 */
    private String channelId;

    /** 渠道编码（例如 PAYERMAX）；调用方用于拼渠道支付通知地址 /channel-callbacks/v1/{channelCode}/{channelAccountId}。 */
    private String channelCode;

    /** MID 当前绑定的渠道技术账号 ID，十进制字符串。 */
    private String channelAccountId;

    /** MID ID，十进制字符串。 */
    private String midId;

    /** 明确支持的渠道操作代码（{@code ACTION.*} 为 true 的操作，取值见 ChannelOperation），按能力代码升序。 */
    private List<String> supportedOperations;

    /** 本次维度匹配到的金额下限（交易币种主单位十进制字符串）；未声明为空。 */
    private String minAmount;

    /** 本次维度匹配到的金额上限（交易币种主单位十进制字符串）；未声明为空。 */
    private String maxAmount;

    /**
     * 本次维度匹配到的 3DS 模式（SECURITY.3DS_MODE）
     * <li>取值 REQUIRED、SUPPORTED、UNSUPPORTED；未声明为空。</li>
     */
    private String threeDsMode;
}
