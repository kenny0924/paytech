package com.paytech.commercial.service.processor.request;

import java.util.List;

import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.ProductCode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** 可开通项新增与修改共用的定义：操作者、名称、归属产品与整体可配维度。 */
@Getter
@Setter
public abstract class ProductCapabilityDefinitionRequest {
    /** 操作者 Account ID */
    @NotNull
    @Positive
    private Long actorId;

    /** 可开通项名称 */
    @NotBlank
    private String capabilityName;

    /** 归属产品 */
    @NotNull
    private ProductCode productCode;

    /** 可配维度整体 */
    @NotNull
    @Valid
    private List<Dimension> dimensions;

    /** 一个可配维度 */
    @Getter
    @Setter
    public static class Dimension {
        /** 维度，须可用于开通接受条件 */
        @NotNull
        private PricingDimension dimensionCode;

        /** 合同开通时是否必填 */
        private boolean required;
    }
}
