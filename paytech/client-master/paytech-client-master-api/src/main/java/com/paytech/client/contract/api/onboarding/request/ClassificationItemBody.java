package com.paytech.client.contract.api.onboarding.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.Data;

/** 一条行业与 MCC 分类。 */
@Data
public class ClassificationItemBody {
    /** 行业代码；必须是 Platform 参考数据中 ACTIVE 的细类（第 2 级）。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String industryCode;
    /** 四位 MCC；必须在 Platform 参考数据中存在且 ACTIVE。 */
    @NotBlank
    @Pattern(regexp = "^[0-9]{4}$")
    private String mccCode;
    /** 是否主要分类；集合中恰好一个。 */
    @NotNull
    private Boolean primary;
}
