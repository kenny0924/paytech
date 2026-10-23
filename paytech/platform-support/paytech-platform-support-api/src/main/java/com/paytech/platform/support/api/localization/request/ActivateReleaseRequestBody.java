package com.paytech.platform.support.api.localization.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 把应用当前发布切换到指定的 READY 发布（上线或回滚）。 */
@Getter
@Setter
public class ActivateReleaseRequestBody {

    /** 目标发布号，必须是该应用 READY 的发布。 */
    @NotNull
    @Min(1)
    private Integer releaseNo;

    /** 切换原因，写入审计，最长 256 字符。 */
    @NotBlank
    @Size(max = 256)
    private String reason;
}
