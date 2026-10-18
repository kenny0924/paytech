package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/** 初始 OWNER 自身的角色授予记录。 */
@Getter
@AllArgsConstructor
@Setter
@NoArgsConstructor
@Accessors(fluent = true)
public class TenantProjectionGrant {
    /** 成员角色授予主键。 */
    private long grantId;

    /** 授予所属工作区。 */
    private long tenantId;

    /** 初始 OWNER 成员。 */
    private long memberId;

    /** 本次系统角色。 */
    private long roleId;
}
