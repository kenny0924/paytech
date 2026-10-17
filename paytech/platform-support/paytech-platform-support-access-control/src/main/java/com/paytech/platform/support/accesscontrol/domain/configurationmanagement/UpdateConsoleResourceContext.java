package com.paytech.platform.support.accesscontrol.domain.configurationmanagement;

import java.time.Instant;
import java.util.Objects;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ConsoleResource;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;
import com.paytech.platform.support.api.enums.OperationsConsoleResourceType;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 当前原对象与完整编辑内容，作为候选结构参与纯规则校验。 */
@Getter
@AllArgsConstructor
public class UpdateConsoleResourceContext implements ConsoleResourceDefinition {
    /** 已读取并核对版本的本表原对象。 */
    private final ConsoleResource resource;

    /** 父资源主键，根节点为空。 */
    private final Long parentResourceId;

    /** 展示名称。 */
    private final String displayName;

    /** 由组件派生的前端路径。 */
    private final String frontendRoute;

    /** 部署组件键，可空。 */
    private final String componentKey;

    /** 关联权限主键。 */
    private final long permissionId;

    /** 同级排序。 */
    private final int sortOrder;

    /** UTC 更新时点。 */
    private final Instant updateTime;

    /** 资源主键。 */
    @Override
    public long getResourceId() {
        return resource.getResourceId();
    }

    /** 不可变资源编码。 */
    @Override
    public String getResourceCode() {
        return resource.getResourceCode();
    }

    /** 菜单或按钮类型。 */
    @Override
    public OperationsConsoleResourceType getResourceType() {
        return resource.getResourceType();
    }

    /** 当前资源状态。 */
    @Override
    public GatewayConfigurationStatus getStatus() {
        return resource.getStatus();
    }

    /** 相同可变内容保留当前版本，不执行无效更新。 */
    public boolean unchanged() {
        return resource.getDisplayName().equals(displayName)
            && Objects.equals(resource.getParentResourceId(), parentResourceId)
            && Objects.equals(resource.getComponentKey(), componentKey)
            && resource.getPermissionId() == permissionId && resource.getSortOrder() == sortOrder
            && Objects.equals(resource.getFrontendRoute(), frontendRoute);
    }
}
