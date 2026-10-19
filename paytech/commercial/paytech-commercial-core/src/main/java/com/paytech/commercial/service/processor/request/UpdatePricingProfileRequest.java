package com.paytech.commercial.service.processor.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** 改写 DRAFT 画像的内部请求。 */
@Getter
@Setter
public class UpdatePricingProfileRequest {
    /** 操作者 Account ID */
    @NotNull
    @Positive
    private Long actorId;

    /** 画像 ID */
    @NotNull
    @Positive
    private Long pricingProfileId;

    /** 调用方期望的画像 BCR Root 版本 */
    @NotNull
    @Positive
    private Long expectedChangeVersion;

    /** 展示名称 */
    @NotBlank
    private String profileName;

    /** Payment Method 代码，可空 */
    private String paymentMethodCode;

    /** 条件行整体 */
    @NotNull
    @Valid
    private List<CreatePricingProfileRequest.Condition> conditions;
}
