package com.paytech.client.master.domain.entityadmin;

import java.util.List;

import com.paytech.client.master.api.enums.PartnerRoleCode;
import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.OperatingEntityScope;
import com.paytech.client.master.core.dal.dataobject.CheckoutRecords.SellerDisclosure;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 运营主体详情：组合行、提供方角色、准入范围与卖方披露。 */
@Getter
@AllArgsConstructor
public class OperatingEntityAdminDetail {
    /** 主档、来源、提供方与根修订号。 */
    private final OperatingEntityAdminView entity;
    /** 提供方当前 ACTIVE 角色；非 PARTNER 来源为空列表。 */
    private final List<PartnerRoleCode> providerRoles;
    /** 全部准入范围。 */
    private final List<OperatingEntityScope> scopes;
    /** 全部卖方披露。 */
    private final List<SellerDisclosure> sellerDisclosures;
}
