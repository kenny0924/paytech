package com.paytech.client.master.core.dal.dataobject;

import java.time.Instant;

import com.paytech.client.master.api.enums.PartnerRoleCode;
import com.paytech.client.master.domain.enums.PartnerRoleStatus;

import lombok.Getter;
import lombok.Setter;

/** 一条 Local Partner 商业角色（t_partner_role）。 */
@Getter
@Setter
public class PartnerRole {
    /** 角色行 ID。 */
    private Long partnerRoleId;
    /** Local Partner ID。 */
    private Long localPartnerId;
    /** 角色 Code。 */
    private PartnerRoleCode roleCode;
    /** 角色行状态。 */
    private PartnerRoleStatus status;
    /** ACTIVE 为 1，ENDED 为空。 */
    private Integer activeSlot;
    /** 创建 UTC 时间。 */
    private Instant createTime;
    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
