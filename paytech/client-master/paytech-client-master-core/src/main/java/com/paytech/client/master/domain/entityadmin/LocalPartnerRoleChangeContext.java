package com.paytech.client.master.domain.entityadmin;

import java.util.List;

import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.LocalPartner;
import com.paytech.client.master.core.dal.dataobject.PartnerRole;
import com.paytech.client.master.core.dal.dataobject.PartyRole;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 整体替换 Local Partner 角色：新增与结束的角色行、对应的统一角色索引行与根推进。 */
@Getter
@AllArgsConstructor
public class LocalPartnerRoleChangeContext {
    /** 修改前的 Local Partner。 */
    private final LocalPartner partner;
    /** 待插入的 ACTIVE 角色行。 */
    private final List<PartnerRole> addedRoles;
    /** 待结束的现有 ACTIVE 角色行。 */
    private final List<PartnerRole> endedRoles;
    /** 与新增角色对应、待插入的统一角色索引行。 */
    private final List<PartyRole> addedPartyRoles;
    /** LOCAL_PARTNER 根推进。 */
    private final RootRevision revision;
}
