package com.paytech.channel.connector.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 创建支付渠道。 */
@Getter
@Setter
public class CreateChannelRequest {
    /** 操作者 Account ID */
    @NotNull
    private Long actorId;

    /** 渠道编码 */
    @NotBlank
    private String channelCode;

    /** 渠道名称 */
    @NotBlank
    private String channelName;
}
