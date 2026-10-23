package com.paytech.platform.support.api.localization.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 登记一个系统支持的语言；新建即为 ENABLED，版本为 1。 */
@Getter
@Setter
public class CreateLocaleRequestBody {

    /**
     * BCP 47 语言代码
     * <li>如 en、zh-CN；语言子标签小写，地区子标签大写，最长 35 字符。创建后不可修改。</li>
     */
    @NotBlank
    @Size(max = 35)
    @Pattern(regexp = "[a-z]{2,3}(-[A-Z][a-z]{3})?(-([A-Z]{2}|[0-9]{3}))?", message = "localeCode 必须是 BCP 47 语言代码，如 en、zh-CN")
    private String localeCode;

    /** 本语言的自称，如“简体中文”，最长 64 字符。 */
    @NotBlank
    @Size(max = 64)
    private String nativeName;

    /** 英文名称，如 Simplified Chinese，最长 64 字符。 */
    @NotBlank
    @Size(max = 64)
    private String englishName;

    /** 文字方向：LTR、RTL。 */
    @NotBlank
    @Pattern(regexp = "LTR|RTL", message = "textDirection 只能为 LTR 或 RTL")
    private String textDirection;

    /** 排序值，0 至 9999，升序排列。 */
    @NotNull
    @Min(0)
    @Max(9999)
    private Integer sortOrder;
}
