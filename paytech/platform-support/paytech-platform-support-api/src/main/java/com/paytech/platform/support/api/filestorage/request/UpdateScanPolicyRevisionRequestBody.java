package com.paytech.platform.support.api.filestorage.request;

import java.util.List;

import jakarta.validation.constraints.*;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 按原版本修改 DRAFT 修订的允许类型与最大字节数；ACTIVE 与 RETIRED 修订不可修改。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateScanPolicyRevisionRequestBody {

    /**
     * 允许的规范 MIME
     * <li>非空、不重复，每项必须属于列表接口返回的 supportedMimeTypes。</li>
     */
    @NotEmpty
    @Size(max = 16)
    private List<@NotBlank @Size(max = 128) String> allowedMimeTypes;

    /**
     * 最大字节数
     * <li>无前导零的正整数十进制字符串，1—104857600（100 MiB）。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,8}")
    private String maxSizeBytes;

    /**
     * 调用方读取的修订原版本
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
