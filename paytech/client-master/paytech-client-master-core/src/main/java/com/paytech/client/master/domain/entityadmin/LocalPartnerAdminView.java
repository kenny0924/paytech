package com.paytech.client.master.domain.entityadmin;

import java.util.List;

import com.paytech.client.master.api.enums.PartnerRoleCode;
import com.paytech.client.master.domain.enums.LocalPartnerStatus;

import lombok.Getter;
import lombok.Setter;

/** 运营查询的 Local Partner 组合行：主档、法定名称、提供数、根修订号与当前 ACTIVE 角色。 */
@Getter
@Setter
public class LocalPartnerAdminView {
    /** Local Partner ID。 */
    private Long localPartnerId;
    /** Local Partner 编号。 */
    private String partnerNo;
    /** 法律主体 Party ID。 */
    private Long partyId;
    /** 法定名称；资料缺失时为空。 */
    private String legalName;
    /** 状态。 */
    private LocalPartnerStatus status;
    /** 以 ACTIVE PARTNER 来源关系提供的经营主体数。 */
    private int providedEntityCount;
    /** LOCAL_PARTNER 根修订号；尚无根时为 1。 */
    private long changeVersion;
    /** 当前 ACTIVE 角色，按 Code 升序；由查询服务在同一次查询中补齐。 */
    private List<PartnerRoleCode> roles = List.of();
}
