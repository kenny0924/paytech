package com.paytech.channel.connector.service.processor.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;

import lombok.Getter;
import lombok.Setter;

/** 渠道、技术账号或 MID 的状态变更；targetId 的含义由所属 Processor 决定。 */
@Getter
@Setter
public class ChangeChannelStatusRequest {
    /** 操作者 Account ID */
    @NotNull
    private Long actorId;

    /** 渠道 ID、技术账号 ID 或 MID ID */
    @NotNull
    private Long targetId;

    /** 目标状态 */
    @NotNull
    private ChannelStatus targetStatus;

    /** 操作者原因，可空 */
    @Size(max = 512)
    private String reason;
}
