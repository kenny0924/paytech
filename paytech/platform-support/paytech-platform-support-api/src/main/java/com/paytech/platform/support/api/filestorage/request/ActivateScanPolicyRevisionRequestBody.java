package com.paytech.platform.support.api.filestorage.request;

import jakarta.validation.constraints.*;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 按原版本激活 DRAFT 修订：原 ACTIVE 修订改为 RETIRED，草稿改为 ACTIVE，同一事务完成；已开始的上传不受影响。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActivateScanPolicyRevisionRequestBody {

    /**
     * 调用方读取的草稿修订原版本
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
