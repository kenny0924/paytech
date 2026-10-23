package com.paytech.platform.support.api.localization.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 修改命名空间名称、说明与排序；编码不可修改。 */
@Getter
@Setter
public class UpdateNamespaceRequestBody {

    /** 调用方读取的原版本。 */
    @NotNull
    @Min(1)
    private Integer expectedVersion;

    /** 命名空间名称，最长 64 字符。 */
    @NotBlank
    @Size(max = 64)
    private String namespaceName;

    /** 说明，最长 512 字符，可为空。 */
    @Size(max = 512)
    private String description;

    /** 排序值，0 至 9999。 */
    @NotNull
    @Min(0)
    @Max(9999)
    private Integer sortOrder;
}
