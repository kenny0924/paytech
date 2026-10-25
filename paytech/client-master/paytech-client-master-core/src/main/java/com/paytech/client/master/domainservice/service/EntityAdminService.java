package com.paytech.client.master.domainservice.service;

import com.paytech.client.master.domain.entityadmin.LocalPartnerRoleChangeContext;
import com.paytech.client.master.domain.entityadmin.OperatingEntityChangeContext;

/** 运营主档维护的本地原子保存；不访问外部服务，不调用其他 Service。 */
public interface EntityAdminService {
    /**
     * 修改 OE 收单资质
     * <li>一个事务：缺根时补建 V1 → 根 CAS → UPDATE 快照 → 写入资质。</li>
     * <li>根版本已变化或并发补建根时抛出 ENTITY_CHANGE_VERSION_CONFLICT 并整体回滚。</li>
     */
    void changeAcquiringQualification(OperatingEntityChangeContext context);

    /**
     * 整体替换 Local Partner 角色
     * <li>一个事务：缺根时补建 V1 → 根 CAS → UPDATE 快照 → 结束去掉的角色及其角色索引 → 插入新增角色及其角色索引。</li>
     * <li>根版本已变化、角色行已被并发修改或唯一键冲突时整体回滚。</li>
     */
    void replacePartnerRoles(LocalPartnerRoleChangeContext context);
}
