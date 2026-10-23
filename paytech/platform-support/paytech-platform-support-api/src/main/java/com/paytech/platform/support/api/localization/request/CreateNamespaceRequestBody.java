package com.paytech.platform.support.api.localization.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 在应用下新建命名空间；版本为 1。 */
@Getter
@Setter
public class CreateNamespaceRequestBody {

    /**
     * 命名空间编码
     * <li>小写字母开头的小写字母、数字与连字符，最长 64 字符，不含点号；应用内唯一，创建后不可修改。</li>
     * <li>其下 key 必须以“编码.”开头，如 team.invite.submit。</li>
     */
    @NotBlank
    @Pattern(regexp = "[a-z][a-z0-9-]{0,63}", message = "namespaceCode 只能使用小写字母、数字与连字符，且以字母开头")
    private String namespaceCode;

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
