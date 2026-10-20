package com.paytech.channel.connector.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 修改支付渠道名称。 */
@Getter
@Setter
public class UpdateChannelRequest {
    /** 操作者 Account ID */
    @NotNull
    private Long actorId;

    /** 渠道 ID */
    @NotNull
    private Long channelId;

    /** 新名称 */
    @NotBlank
    private String channelName;
}
