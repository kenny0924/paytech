package com.paytech.commercial.service.processor.request;

import java.util.List;

import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.PricingProfileKind;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** 创建定价画像的内部请求；pricingProfileCode 是业务唯一身份。 */
@Getter
@Setter
public class CreatePricingProfileRequest {
    /** 操作者 Account ID */
    @NotNull
    @Positive
    private Long actorId;

    /** 画像代码 */
    @NotBlank
    private String pricingProfileCode;

    /** 展示名称 */
    @NotBlank
    private String profileName;

    /** 定价对象种类 */
    @NotNull
    private PricingProfileKind profileKind;

    /** Payment Method 代码，可空 */
    private String paymentMethodCode;

    /** 条件行整体 */
    @NotNull
    @Valid
    private List<Condition> conditions;

    /** 画像的一个等值条件 */
    @Getter
    @Setter
    public static class Condition {
        /** 定价维度 */
        @NotNull
        private PricingDimension dimensionCode;

        /** 规范化值 */
        @NotBlank
        private String conditionValue;
    }
}
