package com.paytech.platform.support.api.filestorage.request;

import jakarta.validation.constraints.*;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 按原版本修改 DRAFT 公开桶的对外基础地址；访问级别与桶名不可修改。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStorageBucketRequestBody {

    /**
     * 对外基础地址
     * <li>https:// 开头，不带查询串、片段或用户信息，不以 / 结尾；修改后需重新检查才能启用。</li>
     */
    @NotBlank
    @Size(max = 255)
    private String publicBaseUrl;

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
