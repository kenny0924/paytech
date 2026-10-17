package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import java.time.Instant;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationMutation;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConsoleResourceDefinition;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;
import com.paytech.platform.support.api.enums.OperationsConsoleResourceType;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** t_console_resource 原对象，只包含本表事实。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConsoleResource implements ConsoleResourceDefinition {
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

    /** 保存前核对当前版本，数据库 CAS 仍承担最终保护。 */
    public void requireVersion(int expectedVersion) {
        if (expectedVersion <= 0 || version != expectedVersion) {
            throw new ConcurrentRequestException("菜单资源已被其他操作修改，请刷新后重试");
        }
    }

    /** 当前对象的稳定业务结果，由 HTTP Converter 负责传输格式。 */
    public ConfigurationMutation mutation() {
        return new ConfigurationMutation("CONSOLE_RESOURCE", resourceId, resourceCode, status.getCode(), version);
    }
}
