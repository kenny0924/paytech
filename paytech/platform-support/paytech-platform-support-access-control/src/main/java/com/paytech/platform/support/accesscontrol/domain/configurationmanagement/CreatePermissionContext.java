package com.paytech.platform.support.accesscontrol.domain.configurationmanagement;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.Permission;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** CreatePermission 本地保存上下文，不承载 HTTP 或基础设施依赖。 */
@Getter
@AllArgsConstructor
public class CreatePermissionContext {
    /** 待插入权限。 */
    private final Permission permission;

}
