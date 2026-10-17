package com.paytech.platform.support.api.operations.response;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Operations 响应字段，保持既有 JSON 合同。 */
public final class PermissionSummary {
    /**
     * 权限 ID。
     * <li>十进制字符串。</li>
     */
    private final String permissionId;

    /** 稳定权限业务代码。 */
    private final String permissionCode;

    /** 资源类型 Code。 */
    private final String resourceType;

    /**
     * 操作代码。
     * <li>查询时可空。</li>
     * <li>非空时精确匹配。</li>
     */
    private final String actionCode;

    /** 权限风险等级 Code。 */
    private final String riskLevel;

    /** 绑定协议字段，保留 JSON 名称与校验约束。 */
    @JsonCreator
    public PermissionSummary(@JsonProperty(value = "permissionId") String permissionId,
        @JsonProperty(value = "permissionCode") String permissionCode,
        @JsonProperty(value = "resourceType") String resourceType,
        @JsonProperty(value = "actionCode") String actionCode,
        @JsonProperty(value = "riskLevel") String riskLevel) {
        this.permissionId = permissionId;
        this.permissionCode = permissionCode;
        this.resourceType = resourceType;
        this.actionCode = actionCode;
        this.riskLevel = riskLevel;
    }

    /** 权限 ID，十进制字符串。 */
    public String getPermissionId() {
        return permissionId;
    }

    /** 稳定权限业务代码。 */
    public String getPermissionCode() {
        return permissionCode;
    }

    /** 资源类型 Code。 */
    public String getResourceType() {
        return resourceType;
    }

    /** 操作代码，查询时可空；非空时精确匹配。 */
    public String getActionCode() {
        return actionCode;
    }

    /** 权限风险等级 Code。 */
    public String getRiskLevel() {
        return riskLevel;
    }

    /** 按协议字段比较值。 */
    @Override
    public boolean equals(Object value) {
        return value instanceof PermissionSummary other && Objects.equals(permissionId, other.permissionId)
            && Objects.equals(permissionCode, other.permissionCode)
            && Objects.equals(resourceType, other.resourceType)
            && Objects.equals(actionCode, other.actionCode)
            && Objects.equals(riskLevel, other.riskLevel);
    }

    /** 与字段值相等语义保持一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(permissionId, permissionCode, resourceType, actionCode, riskLevel);
    }
}
