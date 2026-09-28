package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/** 首次创建的 Tenant 范围系统角色，不复制全平台权限。 */
@Getter
@AllArgsConstructor
@Setter
@NoArgsConstructor
@Accessors(fluent = true)
public class TenantProjectionRole {
    /** Tenant 系统角色主键。 */
    private long roleId;

    /** 角色所属工作区。 */
    private long tenantId;

    /** 入驻负责人或正式 OWNER 的系统角色编码。 */
    private String roleCode;
}
