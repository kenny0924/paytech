package com.paytech.commercial.api.catalog.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** 可开通项停用 / 启用的版本条件。 */
@Getter
@Setter
public class ProductCatalogTransitionRequestBody {
    /** 调用方读取到的目录行 changeVersion；不一致返回 409 */
    @NotNull(message = "expectedChangeVersion 必填")
    @Positive(message = "expectedChangeVersion 必须为正")
    private Long expectedChangeVersion;
}
