package com.paytech.platform.support.api.filestorage.request;

import jakarta.validation.constraints.*;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 按原版本废弃 DRAFT 修订（物理删除；草稿从未被上传引用）。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DiscardScanPolicyRevisionRequestBody {

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
