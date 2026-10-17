package com.paytech.platform.support.api.operations.request;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Operations 请求字段，保持既有 JSON 合同。 */
public final class AssignRolesRequestBody {
    /**
     * 期望的成员授权聚合版本。
     * <li>必须为正整数。</li>
     */
    @Positive
    private final int expectedMemberVersion;

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
    public AssignRolesRequestBody(@JsonProperty(value = "expectedMemberVersion", required = true) int expectedMemberVersion,
        @JsonProperty(value = "roleIds") List<String> roleIds,
        @JsonProperty(value = "reason") String reason) {
        this.expectedMemberVersion = expectedMemberVersion;
        this.roleIds = roleIds == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(roleIds));
        this.reason = reason;
    }

    /** 期望的成员授权聚合版本，必须为正整数。 */
    public int getExpectedMemberVersion() {
        return expectedMemberVersion;
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
        return value instanceof AssignRolesRequestBody other && Objects.equals(expectedMemberVersion, other.expectedMemberVersion)
            && Objects.equals(roleIds, other.roleIds)
            && Objects.equals(reason, other.reason);
    }

    /** 与字段值相等语义保持一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(expectedMemberVersion, roleIds, reason);
    }
}
