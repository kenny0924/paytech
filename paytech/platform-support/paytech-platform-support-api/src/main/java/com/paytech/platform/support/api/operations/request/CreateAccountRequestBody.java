package com.paytech.platform.support.api.operations.request;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Operations 请求字段，保持既有 JSON 合同。 */
public final class CreateAccountRequestBody {
    /**
     * 登录邮箱。
     * <li>按平台 Email 规范化。</li>
     * <li>最长 320 个字符。</li>
     */
    @NotBlank @Size(max = 320)
    private final String email;

    /**
     * 初始密码。
     * <li>首次登录必须修改。</li>
     * <li>不在响应或日志中回显。</li>
     */
    @NotBlank @Size(max = 512)
    private final String initialPassword;

    /**
     * 角色 ID 列表。
     * <li>不允许重复。</li>
     * <li>人员授权必填 1 至 20 项。</li>
     * <li>计数查询为 1 至 200 项。</li>
     */
    @NotEmpty @Size(max = 20)
    private final List<String> roleIds;

    /**
     * 必填操作原因。
     * <li>最长 500 个字符。</li>
     */
    @NotBlank @Size(max = 500)
    private final String reason;

    /** 绑定协议字段，保留 JSON 名称与校验约束。 */
    @JsonCreator
    public CreateAccountRequestBody(@JsonProperty(value = "email") String email,
        @JsonProperty(value = "initialPassword") String initialPassword,
        @JsonProperty(value = "roleIds") List<String> roleIds,
        @JsonProperty(value = "reason") String reason) {
        this.email = email;
        this.initialPassword = initialPassword;
        this.roleIds = roleIds == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(roleIds));
        this.reason = reason;
    }

    /** 登录邮箱；按平台 Email 规范化，最长 320 个字符。 */
    public String getEmail() {
        return email;
    }

    /** 初始密码，首次登录必须修改；不在响应或日志中回显。 */
    public String getInitialPassword() {
        return initialPassword;
    }

    /** 角色 ID 列表，不允许重复；人员授权必填 1 至 20 项，计数查询为 1 至 200 项。 */
    public List<String> getRoleIds() {
        return roleIds;
    }

    /** 必填操作原因，最长 500 个字符。 */
    public String getReason() {
        return reason;
    }

    /** 按协议字段比较值。 */
    @Override
    public boolean equals(Object value) {
        return value instanceof CreateAccountRequestBody other && Objects.equals(email, other.email)
            && Objects.equals(initialPassword, other.initialPassword)
            && Objects.equals(roleIds, other.roleIds)
            && Objects.equals(reason, other.reason);
    }

    /** 与字段值相等语义保持一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(email, initialPassword, roleIds, reason);
    }
    /** 初始密码和登录邮箱不得进入日志。 */
    @Override
    public String toString() {
        return "CreateAccountRequestBody[REDACTED]";
    }
}
