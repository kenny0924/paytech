package com.paytech.platform.support.accesscontrol.domain.configurationmanagement;

import java.time.Instant;
import java.util.List;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.PermissionStatus;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;
import com.paytech.platform.support.api.enums.OperationsConsoleResourceType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 资源与权限组合查询及当前维护边界，不承担持久化。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConsoleResourceView implements ConsoleResourceDefinition {
    /** 资源主键。 */
    private long resourceId;

    /** 不可变资源编码。 */
    private String resourceCode;

    /** 所属 Channel。 */
    private GatewayRouteChannel channel;

    /** 菜单或按钮类型。 */
    private OperationsConsoleResourceType resourceType;

    /** 父资源主键，根节点为空。 */
    private Long parentResourceId;

    /** 展示名称。 */
    private String displayName;

    /** 由组件派生的前端路径。 */
    private String frontendRoute;

    /** 部署组件键，可空。 */
    private String componentKey;

    /** 关联权限主键。 */
    private long permissionId;

    /** 当前权限编码。 */
    private String permissionCode;

    /** 当前权限状态，可空。 */
    private PermissionStatus permissionStatus;

    /** 同级排序。 */
    private int sortOrder;

    /** 当前资源状态。 */
    private GatewayConfigurationStatus status;

    /** 当前乐观锁版本。 */
    private int version;

    /** UTC 创建时点。 */
    private Instant createTime;

    /** UTC 更新时点。 */
    private Instant updateTime;

    /** 当前管理入口保护。 */
    private ManagementProtection managementProtection;

    /** 当前制品是否支持该实例。 */
    private boolean supported;

    /** 只用当前查询事实补充维护边界和部署支持标记。 */
    public void prepare(ConsoleResourcePolicy policy, List<ConsoleResourceView> tree) {
        managementProtection = policy.protection(this, tree);
        supported = policy.supported(this, tree);
    }
}
