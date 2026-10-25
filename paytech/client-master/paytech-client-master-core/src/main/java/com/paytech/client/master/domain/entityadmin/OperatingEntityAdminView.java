package com.paytech.client.master.domain.entityadmin;

import java.time.Instant;

import com.paytech.client.master.api.enums.AcquiringQualification;
import com.paytech.client.master.domain.enums.EntitySourceRelationSourceType;
import com.paytech.client.master.domain.enums.LocalPartnerStatus;
import com.paytech.client.master.domain.enums.OperatingEntityStatus;

import lombok.Getter;
import lombok.Setter;

/** 运营查询的 OE 组合行：主档、当前 ACTIVE 来源、PARTNER 来源的提供方、法定名称与根修订号。 */
@Getter
@Setter
public class OperatingEntityAdminView {
    /** OE 编号。 */
    private String entityNo;
    /** OE 法律主体 Party ID。 */
    private Long partyId;
    /** OE 法定名称；资料缺失时为空。 */
    private String legalName;
    /** 经营国家。 */
    private String operatingCountry;
    /** OE 状态。 */
    private OperatingEntityStatus status;
    /** 收单资质。 */
    private AcquiringQualification acquiringQualification;
    /** 当前 ACTIVE 来源关系 ID；没有时为空。 */
    private Long sourceRelationId;
    /** 当前来源类型；没有时为空。 */
    private EntitySourceRelationSourceType sourceType;
    /** CLIENT 来源的 Client ID。 */
    private Long sourceClientId;
    /** PARTNER 来源的 Local Partner ID。 */
    private Long sourcePartnerId;
    /** 来源生效开始时间。 */
    private Instant sourceEffectiveFrom;
    /** 来源生效结束时间。 */
    private Instant sourceEffectiveTo;
    /** 提供方编号；非 PARTNER 来源为空。 */
    private String providerPartnerNo;
    /** 提供方 Party ID。 */
    private Long providerPartyId;
    /** 提供方状态。 */
    private LocalPartnerStatus providerStatus;
    /** 提供方法定名称。 */
    private String providerLegalName;
    /** OPERATING_ENTITY 根修订号；尚无根时为 1。 */
    private long changeVersion;
}
