package com.paytech.platform.support.api.filestorage.request;

import jakarta.validation.constraints.*;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 按原版本切换存储实例或存储桶状态。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChangeStorageStatusRequestBody {

    /**
     * 目标状态
     * <li>实例：{@code FileStorageInstanceStatus} Code ACTIVE、DISABLED；桶：{@code FileStorageBucketStatus} Code ACTIVE、READ_ONLY、DISABLED。</li>
     */
    @NotBlank
    @Pattern(regexp = "ACTIVE|READ_ONLY|DISABLED")
    private String status;

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
