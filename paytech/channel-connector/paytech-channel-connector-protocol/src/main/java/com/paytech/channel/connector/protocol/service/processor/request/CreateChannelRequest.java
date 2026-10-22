package com.paytech.channel.connector.protocol.service.processor.request;

import com.paytech.channel.connector.api.enums.ChannelType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 创建 DRAFT 渠道。 */
@Getter
@Setter
public class CreateChannelRequest {
    /** 操作者 Account ID */
    @NotNull
    private Long actorId;

    /** 渠道编码，格式已由入口校验 */
    @NotBlank
    private String channelCode;

    /** 渠道名称 */
    @NotBlank
    private String channelName;

    /** 渠道类型，创建后不可修改 */
    @NotNull
    private ChannelType channelType;

    /** 渠道说明，可空 */
    @Size(max = 512)
    private String description;
}
