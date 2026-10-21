package com.paytech.commercial.service.processor.request;

import com.paytech.commercial.api.enums.CapabilityKind;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 新增可开通项的内部请求；capabilityCode 是业务唯一身份。 */
@Getter
@Setter
public class CreateProductCapabilityRequest extends ProductCapabilityDefinitionRequest {
    /** 可开通项代码 */
    @NotBlank
    private String capabilityCode;

    /** 种类 */
    @NotNull
    private CapabilityKind capabilityKind;

    /** 支付方式代码，仅 PAYMENT_METHOD */
    private String paymentMethodCode;
}
