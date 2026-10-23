package com.paytech.platform.support.api.filestorage.request;

import jakarta.validation.constraints.*;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 按原版本停用当前 ACTIVE 修订且不补新修订；有写入路由引用该代码时返回 400。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RetireScanPolicyRequestBody {

    /**
     * 调用方读取的 ACTIVE 修订原版本
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
