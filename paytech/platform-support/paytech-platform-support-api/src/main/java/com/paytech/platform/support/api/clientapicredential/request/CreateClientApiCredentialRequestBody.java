package com.paytech.platform.support.api.clientapicredential.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 创建 API 凭证。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateClientApiCredentialRequestBody {

    /** 显示名称；去除首尾空白后 1..64 字符，不允许控制字符。 */
    @NotBlank
    @Size(max = 64)
    private String name;

    /** 授予的 Scope 编码；1..64 个、不重复，必须在 Scope 目录内。 */
    @NotEmpty
    @Size(max = 64)
    private List<@NotNull String> scopeCodes;

    /** 有效期天数，只能是 30、90、180 或 365。 */
    private int validityDays;
}
