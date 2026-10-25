package com.paytech.client.master.domainservice.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.client.master.core.dal.dataobject.PartnerRole;

/** t_partner_role 的本地仓储；不负责其他表的加载或事务。 */
public interface PartnerRoleRepository {
    /** 读取本 Client Checkout 范围内 PARTNER 来源 Local Partner 的 ACTIVE 角色。 */
    List<PartnerRole> findActiveForCheckout(long clientId);

    /** 读取一组 Local Partner 的 ACTIVE 角色；空集合返回空列表。 */
    List<PartnerRole> findActiveByPartners(Collection<Long> localPartnerIds);

    /** 插入一条 ACTIVE 角色行；同角色已 ACTIVE 时由唯一键拒绝并整体回滚。 */
    void insert(PartnerRole role);

    /** 结束一条 ACTIVE 角色行；已被结束时抛出并发冲突。成功后同步原对象。 */
    void end(PartnerRole role, Instant now);
}
