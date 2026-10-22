package com.paytech.channel.connector.protocol.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 新建服务渠道调用授权。 */
@Getter
@Setter
public class CreateChannelCallGrantRequest {
    /** 操作者 Account ID */
    @NotNull
    private Long actorId;

    /** 服务渠道 ID */
    @NotNull
    private Long channelId;

    /** 调用方服务名 */
    @NotBlank
    @Pattern(regexp = "[a-z][a-z0-9-]{1,63}")
    private String callerService;

    /** 标准 API 编码 */
    @NotBlank
    private String standardApiCode;
}
