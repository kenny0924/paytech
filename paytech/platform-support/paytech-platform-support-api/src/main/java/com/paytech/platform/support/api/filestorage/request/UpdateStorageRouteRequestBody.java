package com.paytech.platform.support.api.filestorage.request;

import jakarta.validation.constraints.*;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 按原版本修改写入路由的目标桶、扫描策略与上传期限；只影响之后的新上传。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStorageRouteRequestBody {

    /** 目标实例代码；实例必须为 ACTIVE。 */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private String instanceCode;

    /** 目标存储桶名称；存储桶必须为 ACTIVE。 */
    @NotBlank
    @Pattern(regexp = "[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")
    private String bucketName;

    /**
     * 扫描策略代码
     * <li>必须存在 ACTIVE 修订；新上传冻结当时的 ACTIVE 修订。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private String scanPolicyCode;

    /** 上传授权有效秒数，60—900。 */
    @NotNull
    @Min(60)
    @Max(900)
    private Integer maxUploadSeconds;

    /**
     * 调用方读取的原版本
     * <li>正整数；版本不符返回 409。</li>
     */
    @NotNull
    @Positive
    private Integer expectedVersion;

    /**
     * 操作原因
     * <li>必填，最多 500 字符；保存时去除首尾空白，写入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private String reason;
}
