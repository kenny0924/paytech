package com.paytech.platform.support.api.localization.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 修改语言的名称、文字方向、状态与排序；全量提交。 */
@Getter
@Setter
public class UpdateLocaleRequestBody {

    /** 调用方读取的原版本。 */
    @NotNull
    @Min(1)
    private Integer expectedVersion;

    /** 本语言的自称，最长 64 字符。 */
    @NotBlank
    @Size(max = 64)
    private String nativeName;

    /** 英文名称，最长 64 字符。 */
    @NotBlank
    @Size(max = 64)
    private String englishName;

    /** 文字方向：LTR、RTL。 */
    @NotBlank
    @Pattern(regexp = "LTR|RTL", message = "textDirection 只能为 LTR 或 RTL")
    private String textDirection;

    /**
     * 状态：ENABLED、DISABLED
     * <li>en 不能停用；被任一应用用作默认语言或必须覆盖语言时不能停用。</li>
     */
    @NotBlank
    @Pattern(regexp = "ENABLED|DISABLED", message = "status 只能为 ENABLED 或 DISABLED")
    private String status;

    /** 排序值，0 至 9999。 */
    @NotNull
    @Min(0)
    @Max(9999)
    private Integer sortOrder;
}
