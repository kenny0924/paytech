package com.paytech.platform.support.accesscontrol.domain.tenantaccess;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjection;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionGrant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionMember;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.TenantProjectionRole;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 首次来源开通的完整本地保存上下文。 */
@Getter
@AllArgsConstructor
public class ProvisionTenantContext {
    /** 本次新建的 PENDING Tenant 对象。 */
    private final TenantProjection tenant;

    /** 身份已确认的初始 OWNER 对象。 */
    private final TenantProjectionMember member;

    /** 本次创建的入驻负责人角色。 */
    private final TenantProjectionRole role;

    /** 本次创建的入驻角色授予。 */
    private final TenantProjectionGrant grant;

    /** 四表统一使用的 UTC 微秒时间。 */
    private final Instant createTime;
}
