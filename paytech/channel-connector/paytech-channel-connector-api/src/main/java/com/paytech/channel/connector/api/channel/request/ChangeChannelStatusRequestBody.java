package com.paytech.channel.connector.api.channel.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 渠道、技术账号与 MID 共用的状态变更请求。 */
@Getter
@Setter
public class ChangeChannelStatusRequestBody {
    /**
     * 目标状态
     * <li>ACTIVE、SUSPENDED 或 CLOSED；合法迁移为 DRAFT→ACTIVE、ACTIVE⇄SUSPENDED、DRAFT|ACTIVE|SUSPENDED→CLOSED。</li>
     * <li>CLOSED 为终态。</li>
     */
    @NotBlank(message = "目标状态必填")
    @Pattern(regexp = "ACTIVE|SUSPENDED|CLOSED", message = "目标状态只能为 ACTIVE、SUSPENDED 或 CLOSED")
    private String targetStatus;

    /**
     * 变更原因
     * <li>可空，最长 512 字符；写入变更记录，禁止填写凭据。</li>
     */
    @Size(max = 512, message = "原因最长 512 字符")
    private String reason;
}
