package com.paytech.channel.connector.api.grant.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 启用或停用调用授权请求。 */
@Getter
@Setter
public class ChangeChannelCallGrantStatusRequestBody {
    /** 目标状态：ACTIVE 或 DISABLED */
    @NotBlank(message = "目标状态必填")
    @Pattern(regexp = "ACTIVE|DISABLED", message = "目标状态只能为 ACTIVE 或 DISABLED")
    private String targetStatus;

    /** 期望的授权版本；与当前版本不一致时 409 */
    @NotNull(message = "期望版本必填")
    @Min(value = 1, message = "期望版本必须为正整数")
    private Integer expectedVersion;

    /**
     * 变更原因
     * <li>可空，最长 512 字符；写入变更记录，禁止填写凭据。</li>
     */
    @Size(max = 512, message = "原因最长 512 字符")
    private String reason;
}
