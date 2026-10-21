package com.paytech.commercial.service.processor.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** 改写可开通项名称、归属产品与可配维度的内部请求。 */
@Getter
@Setter
public class UpdateProductCapabilityRequest extends ProductCapabilityDefinitionRequest {
    /** 可开通项 ID */
    @NotNull
    @Positive
    private Long productCapabilityId;

    /** 期望的目录行版本 */
    @NotNull
    @Positive
    private Long expectedChangeVersion;
}
