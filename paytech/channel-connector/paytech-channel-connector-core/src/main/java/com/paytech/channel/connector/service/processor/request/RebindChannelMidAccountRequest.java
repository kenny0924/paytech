package com.paytech.channel.connector.service.processor.request;

import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** MID 改绑技术账号。 */
@Getter
@Setter
public class RebindChannelMidAccountRequest {
    /** 操作者 Account ID */
    @NotNull
    private Long actorId;

    /** MID ID */
    @NotNull
    private Long midId;

    /** 目标技术账号 ID */
    @NotNull
    private Long channelAccountId;
}
