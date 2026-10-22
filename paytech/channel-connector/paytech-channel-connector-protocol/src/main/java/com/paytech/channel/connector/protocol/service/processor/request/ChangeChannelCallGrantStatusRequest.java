package com.paytech.channel.connector.protocol.service.processor.request;

import com.paytech.channel.connector.api.enums.ChannelCallGrantStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 启用或停用调用授权。 */
@Getter
@Setter
public class ChangeChannelCallGrantStatusRequest {
    /** 操作者 Account ID */
    @NotNull
    private Long actorId;

    /** 授权 ID */
    @NotNull
    private Long callGrantId;

    /** 目标状态 */
    @NotNull
    private ChannelCallGrantStatus targetStatus;

    /** 调用方读取的授权版本 */
    @NotNull
    @Positive
    private Integer expectedVersion;

    /** 操作者原因，可空 */
    @Size(max = 512)
    private String reason;
}
