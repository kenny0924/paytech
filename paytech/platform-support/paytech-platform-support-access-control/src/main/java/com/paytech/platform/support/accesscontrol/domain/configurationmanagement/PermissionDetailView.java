package com.paytech.platform.support.accesscontrol.domain.configurationmanagement;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.Permission;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationCatalog.ReferenceCounts;

import lombok.Getter;

/** 权限、维护边界与配置引用计数的组合查询结果。 */
@Getter
public class PermissionDetailView extends PermissionSummary {
    /** 当前维护边界。 */
    private final ManagementProtection managementProtection;
    /** 当前配置引用计数，不承诺跨表快照。 */
    private final ReferenceCounts referenceCounts;

    /** 冻结权限与本次关联查询结果。 */
    public PermissionDetailView(Permission permission, ManagementProtection protection, ReferenceCounts counts) {
        super(permission);
        this.managementProtection = protection;
        this.referenceCounts = counts;
    }
}
