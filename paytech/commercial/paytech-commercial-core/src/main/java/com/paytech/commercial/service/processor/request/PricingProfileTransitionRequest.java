package com.paytech.commercial.service.processor.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** 画像激活或退役的内部请求。 */
@Getter
@Setter
public class PricingProfileTransitionRequest {
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
}
