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
}
