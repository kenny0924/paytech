package com.paytech.platform.support.api.filestorage.request;

import jakarta.validation.constraints.*;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 新建写入路由：来源系统的新上传写入指定存储桶并冻结扫描策略。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateStorageRouteRequestBody {

    /**
     * 来源系统
     * <li>内部调用身份的服务名 [a-z][a-z0-9-]{0,63}，或 * 表示默认路由；创建后不可修改。</li>
     */
    @NotBlank
    @Pattern(regexp = "\\*|[a-z][a-z0-9-]{0,63}")
    private String sourceSystem;

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
     * 操作原因
     * <li>必填，最多 500 字符；保存时去除首尾空白，写入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private String reason;
}
