package com.paytech.client.master.core.dal.dataobject;

import java.time.Instant;

import com.paytech.client.master.domain.enums.PartyRoleCode;
import com.paytech.client.master.domain.enums.PartyRoleStatus;

import lombok.Getter;
import lombok.Setter;

/** 一条法律主体统一角色索引（t_party_role）。 */
@Getter
@Setter
public class PartyRole {
    /** 角色行 ID。 */
    private Long partyRoleId;
    /** 法律主体 Party ID。 */
    private Long partyId;
    /** 角色 Code。 */
    private PartyRoleCode roleCode;
    /**
     * 角色主体标识
     * <li>CLIENT 为 client_id，OPERATING_ENTITY 为 entity_no，OE_PROVIDER / REFERRER 为 local_partner_id 的十进制文本。</li>
     */
    private String subjectId;
    /** 角色行状态。 */
    private PartyRoleStatus status;
    /** ACTIVE 为 1，ENDED 为空。 */
    private Integer activeSlot;
    /** 生效开始 UTC 时间。 */
    private Instant effectiveFrom;
    /** 生效结束 UTC 时间；ACTIVE 为空。 */
    private Instant effectiveTo;
    /** 创建 UTC 时间。 */
    private Instant createTime;
    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
