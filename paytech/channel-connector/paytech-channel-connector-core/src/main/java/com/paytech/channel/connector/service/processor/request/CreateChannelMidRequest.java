package com.paytech.channel.connector.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 创建 MID。 */
@Getter
@Setter
public class CreateChannelMidRequest {
    /** 操作者 Account ID */
    @NotNull
    private Long actorId;

    /** 所属渠道 ID */
    @NotNull
    private Long channelId;

    /** 绑定的技术账号 ID */
    @NotNull
    private Long channelAccountId;

    /** 渠道分配的商户号 */
    @NotBlank
    private String externalMidCode;

    /** 所属 OE 编号 */
    @NotBlank
    private String entityNo;
}
