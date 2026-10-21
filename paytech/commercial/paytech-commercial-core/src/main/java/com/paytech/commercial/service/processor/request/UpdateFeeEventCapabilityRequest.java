package com.paytech.commercial.service.processor.request;

import com.paytech.commercial.api.enums.CapabilityKind;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 改写计费事件挂接开通项的内部请求。 */
@Getter
@AllArgsConstructor
public class UpdateFeeEventCapabilityRequest {
    /** 操作者 Account ID */
    @NotNull
    @Positive
    private final Long actorId;

    /** 计费事件登记 ID */
    @NotNull
    @Positive
    private final Long productFeeEventId;

    /** 期望的目录行版本 */
    @NotNull
    @Positive
    private final Long expectedChangeVersion;

    /** 挂接种类，可空 */
    private final CapabilityKind capabilityKind;

    /** 功能开通项代码，仅 FEATURE */
    private final String capabilityCode;
}
