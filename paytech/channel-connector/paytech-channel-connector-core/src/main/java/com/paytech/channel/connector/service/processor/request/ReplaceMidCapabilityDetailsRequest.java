package com.paytech.channel.connector.service.processor.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 整组替换能力明细。 */
@Getter
@Setter
public class ReplaceMidCapabilityDetailsRequest {
    /** 操作者 Account ID */
    @NotNull
    private Long actorId;

    /** 能力组 ID */
    @NotNull
    private Long midCapabilityId;

    /** 替换后的全部明细 */
    @NotEmpty
    private List<@NotNull @Valid MidCapabilityDetailItem> details;
}
