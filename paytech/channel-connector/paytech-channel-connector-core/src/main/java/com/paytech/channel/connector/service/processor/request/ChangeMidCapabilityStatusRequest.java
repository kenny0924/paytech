package com.paytech.channel.connector.service.processor.request;

import jakarta.validation.constraints.NotNull;

import com.paytech.channel.connector.core.enums.CapabilityStatus;

import lombok.Getter;
import lombok.Setter;

/** 能力条件组整组启停。 */
@Getter
@Setter
public class ChangeMidCapabilityStatusRequest {
    /** 操作者 Account ID */
    @NotNull
    private Long actorId;

    /** 能力组 ID */
    @NotNull
    private Long midCapabilityId;

    /** 目标状态 */
    @NotNull
    private CapabilityStatus targetStatus;
}
