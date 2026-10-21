package com.paytech.commercial.service.processor.request;

import com.paytech.commercial.api.enums.ProductStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 停用 / 启用可开通项的内部请求。 */
@Getter
@AllArgsConstructor
public class TransitionProductCapabilityRequest {
    /** 操作者 Account ID */
    @NotNull
    @Positive
    private final Long actorId;

    /** 可开通项 ID */
    @NotNull
    @Positive
    private final Long productCapabilityId;

    /** 期望的目录行版本 */
    @NotNull
    @Positive
    private final Long expectedChangeVersion;

    /** 目标状态：RETIRED（停用）或 ACTIVE（启用） */
    @NotNull
    private final ProductStatus targetStatus;
}
