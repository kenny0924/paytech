package com.paytech.platform.support.accesscontrol.domain.configurationmanagement;

import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;
import com.paytech.platform.support.api.enums.OperationsConsoleResourceType;

/** 资源结构规则所需的只读事实，由持久化对象、查询 View 和编辑 Context 共同实现。 */
public interface ConsoleResourceDefinition {
    /** 资源主键。 */
    long getResourceId();

    /** 不可变资源编码。 */
    String getResourceCode();

    /** 菜单或按钮类型。 */
    OperationsConsoleResourceType getResourceType();

    /** 父资源主键，根节点为空。 */
    Long getParentResourceId();

    /** 展示名称。 */
    String getDisplayName();

    /** 由组件派生的前端路径。 */
    String getFrontendRoute();

    /** 部署组件键，可空。 */
    String getComponentKey();

    /** 关联权限主键。 */
    long getPermissionId();

    /** 同级排序。 */
    int getSortOrder();

    /** 当前资源状态。 */
    GatewayConfigurationStatus getStatus();

}
