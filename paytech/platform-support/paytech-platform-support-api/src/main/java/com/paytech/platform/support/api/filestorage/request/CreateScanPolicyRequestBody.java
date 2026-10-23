package com.paytech.platform.support.api.filestorage.request;

import java.util.List;

import jakarta.validation.constraints.*;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 新建检查策略代码及其修订 1（DRAFT）；激活后才能被写入路由选用。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateScanPolicyRequestBody {

    /**
     * 策略代码
     * <li>[A-Z][A-Z0-9_]{0,63}；创建后不可修改，已存在返回 400。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private String policyCode;

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
     * 操作原因
     * <li>必填，最多 500 字符；保存时去除首尾空白，写入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private String reason;
}
