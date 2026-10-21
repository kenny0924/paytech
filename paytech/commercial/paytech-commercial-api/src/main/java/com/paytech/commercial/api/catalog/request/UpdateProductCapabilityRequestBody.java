package com.paytech.commercial.api.catalog.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 整体改写可开通项的名称、归属产品与可配维度；代码与种类不可改。 */
@Getter
@Setter
public class UpdateProductCapabilityRequestBody {
    /** 调用方读取到的可开通项 changeVersion；不一致返回 409 */
    @NotNull(message = "expectedChangeVersion 必填")
    @Positive(message = "expectedChangeVersion 必须为正")
    private Long expectedChangeVersion;

    /** 可开通项名称 */
    @NotBlank(message = "capabilityName 必填")
    @Size(max = 128, message = "capabilityName 最长 128 字符")
    private String capabilityName;

    /** 所属产品代码，须 ACTIVE */
    @NotBlank(message = "productCode 必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "productCode 必须是大写稳定代码")
    private String productCode;

    /** 可配维度，整体提交，不得重复 */
    @NotNull(message = "dimensions 必填，可为空列表")
    @Size(max = 20, message = "dimensions 最多 20 个")
    @Valid
    private List<CapabilityDimensionEntry> dimensions;
}
