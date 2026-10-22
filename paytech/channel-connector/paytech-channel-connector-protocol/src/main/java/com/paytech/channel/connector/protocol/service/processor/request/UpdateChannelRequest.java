package com.paytech.channel.connector.protocol.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 修改渠道名称与说明（整体替换）。 */
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

    /** 新说明，null 表示清空 */
    @Size(max = 512)
    private String description;
}
