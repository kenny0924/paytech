package com.paytech.payment.channel.domain;


import lombok.Getter;
import lombok.Setter;

/** 执行边界专用的多表查询结果；不提供默认 toString，不作为 HTTP 响应。 */
@Getter
@Setter
public class ChannelExecutionConfigView {
    /** 冻结渠道代码。 */
    private String channelCode;

    /** 冻结 MID 的外部商户号。 */
    private String externalMidCode;

    /** Vault 还原并核对归属后的账号配置，仅本次受控执行使用。 */
    private ChannelAccountConfiguration accountConfiguration;
}
