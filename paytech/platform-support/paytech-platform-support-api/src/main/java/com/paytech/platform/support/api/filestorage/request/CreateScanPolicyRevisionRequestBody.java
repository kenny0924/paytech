package com.paytech.platform.support.api.filestorage.request;

import jakarta.validation.constraints.*;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 以当前 ACTIVE 修订为模板新建 DRAFT 修订；同一代码已有 DRAFT 时返回 409。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateScanPolicyRevisionRequestBody {

    /**
     * 操作原因
     * <li>必填，最多 500 字符；保存时去除首尾空白，写入审计。</li>
     */
    @NotBlank
    @Size(max = 500)
    private String reason;
}
