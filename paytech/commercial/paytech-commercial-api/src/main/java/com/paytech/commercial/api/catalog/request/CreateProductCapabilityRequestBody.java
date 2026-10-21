package com.paytech.commercial.api.catalog.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 新增可开通项（ACTIVE）及其可配维度。
 * <li>capabilityCode 全局唯一，重复返回 409；所属产品须 ACTIVE。</li>
 * <li>PAYMENT_METHOD 必须给出 paymentMethodCode，其他种类必须为空。</li>
 */
@Getter
@Setter
public class CreateProductCapabilityRequestBody {
    /** 所属产品代码，如 ACQUIRING */
    @NotBlank(message = "productCode 必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "productCode 必须是大写稳定代码")
    private String productCode;

    /** 可开通项代码，创建后不可修改 */
    @NotBlank(message = "capabilityCode 必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "capabilityCode 必须以大写字母开头，2–64 位大写字母、数字或下划线")
    private String capabilityCode;

    /** 可开通项名称 */
    @NotBlank(message = "capabilityName 必填")
    @Size(max = 128, message = "capabilityName 最长 128 字符")
    private String capabilityName;

    /** 可开通项种类，创建后不可修改 */
    @NotBlank(message = "capabilityKind 必填")
    @Pattern(regexp = "PAYMENT_METHOD|FEATURE|PAYOUT_RAIL|FX|DISPUTE|PERIODIC_SERVICE",
        message = "capabilityKind 只能为 PAYMENT_METHOD、FEATURE、PAYOUT_RAIL、FX、DISPUTE、PERIODIC_SERVICE")
    private String capabilityKind;

    /** 支付方式代码，PAYMENT_METHOD 必填，其他种类必须为空 */
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "paymentMethodCode 必须是大写稳定代码")
    private String paymentMethodCode;

    /** 可配维度，整体提交，不得重复 */
    @NotNull(message = "dimensions 必填，可为空列表")
    @Size(max = 20, message = "dimensions 最多 20 个")
    @Valid
    private List<CapabilityDimensionEntry> dimensions;
}
