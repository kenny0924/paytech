package com.paytech.client.master.api.entityadmin.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 运营主体详情；收单资质修改后返回同一结构。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OperatingEntityDetailResponseBody {
    /** OE 不可变五位编号。 */
    private String entityNo;

    /** OE 法律主体 Party ID，十进制字符串。 */
    private String partyId;

    /** OE 法定名称；资料缺失时为 null。 */
    private String legalName;

    /** 经营国家，ISO 3166-1 alpha-2。 */
    private String operatingCountry;

    /** OE 状态 Code。 */
    private String status;

    /** 收单资质 Code：MERCHANT、MERCHANT_RESALE_APPROVED 或 PAYFAC。 */
    private String acquiringQualification;

    /** 根修订号，修改时作为 expectedChangeVersion 回传；尚未建立审计根时为 1。 */
    private long changeVersion;

    /** 当前 ACTIVE 来源关系；没有时为 null。 */
    private SourceRelation sourceRelation;

    /** 提供方；仅 PARTNER 来源有值。 */
    private Provider provider;

    /** 准入范围（只读），按 scopeId 升序。 */
    private List<Scope> scopes;

    /** 卖方披露摘要（只读），按 disclosureId 升序。 */
    private List<SellerDisclosureSummary> sellerDisclosures;

    /** 来源关系。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceRelation {
        /** 来源关系 ID。 */
        private String sourceRelationId;

        /** 来源类型 Code：CLIENT 或 PARTNER。 */
        private String sourceType;

        /** CLIENT 来源的 Client ID，其他为 null。 */
        private String sourceClientId;

        /** PARTNER 来源的 Local Partner ID，其他为 null。 */
        private String sourcePartnerId;

        /** 生效开始时间，UTC RFC 3339，六位小数。 */
        private String effectiveFrom;

        /** 生效结束时间；不限期为 null。 */
        private String effectiveTo;
    }

    /** 提供方。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Provider {
        /** Local Partner ID。 */
        private String localPartnerId;

        /** Local Partner 编号。 */
        private String partnerNo;

        /** Local Partner 法律主体 Party ID。 */
        private String partyId;

        /** Local Partner 法定名称；资料缺失时为 null。 */
        private String legalName;

        /** Local Partner 状态 Code。 */
        private String status;

        /** 当前 ACTIVE 角色 Code，升序。 */
        private List<String> roles;
    }

    /** 准入范围。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Scope {
        /** 范围 ID。 */
        private String scopeId;

        /** 消费者国家。 */
        private String consumerCountryCode;

        /** 币种。 */
        private String currency;

        /** 行业代码。 */
        private String industryCode;

        /** 四位 MCC。 */
        private String mccCode;

        /** 范围状态 Code：DRAFT、ACTIVE 或 ENDED。 */
        private String status;

        /** 生效开始时间；DRAFT 可为 null。 */
        private String effectiveFrom;

        /** 生效结束时间；不限期为 null。 */
        private String effectiveTo;
    }

    /** 卖方披露摘要。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SellerDisclosureSummary {
        /** 披露 ID。 */
        private String disclosureId;

        /** 适用的消费者国家。 */
        private String countryCode;

        /** 适用的 Locale。 */
        private String locale;

        /** 披露状态 Code。 */
        private String status;

        /** 生效开始时间。 */
        private String effectiveFrom;

        /** 生效结束时间；不限期为 null。 */
        private String effectiveTo;
    }
}
