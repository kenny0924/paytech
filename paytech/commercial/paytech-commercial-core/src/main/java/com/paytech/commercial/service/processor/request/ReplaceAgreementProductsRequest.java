package com.paytech.commercial.service.processor.request;

import java.util.List;

import com.paytech.commercial.api.enums.ProductCode;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** 整体替换订阅产品的内部请求。 */
@Getter
@Setter
public class ReplaceAgreementProductsRequest {
    /** 操作者 Account ID */
    @NotNull
    @Positive
    private Long actorId;

    /** 合同 ID */
    @NotNull
    @Positive
    private Long agreementId;

    /** 调用方期望的合同 BCR Root 版本 */
    @NotNull
    @Positive
    private Long expectedChangeVersion;

    /** 订阅的产品代码 */
    @NotNull
    private List<ProductCode> productCodes;
}
