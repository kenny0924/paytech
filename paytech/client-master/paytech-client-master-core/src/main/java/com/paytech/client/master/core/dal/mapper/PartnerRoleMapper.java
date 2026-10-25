package com.paytech.client.master.core.dal.mapper;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.client.master.core.dal.dataobject.PartnerRole;

/** t_partner_role 的持久化读写。 */
public interface PartnerRoleMapper {
    /** 读取本 Client Checkout 范围内 PARTNER 来源 Local Partner 的 ACTIVE 角色。 */
    List<PartnerRole> findActiveForCheckout(@Param("clientId") long clientId);

    /** 读取一组 Local Partner 的 ACTIVE 角色；集合不能为空。 */
    List<PartnerRole> findActiveByPartners(@Param("localPartnerIds") Collection<Long> localPartnerIds);

    /** 插入一条角色行。 */
    void insert(@Param("row") PartnerRole row);

    /** ACTIVE 条件结束一条角色行，返回影响行数。 */
    int end(@Param("partnerRoleId") long partnerRoleId, @Param("now") Instant now);
}
