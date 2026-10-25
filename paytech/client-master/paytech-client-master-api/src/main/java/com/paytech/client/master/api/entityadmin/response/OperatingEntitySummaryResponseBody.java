package com.paytech.client.master.api.entityadmin.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 运营主体列表中的一行。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OperatingEntitySummaryResponseBody {
    /** OE 不可变五位编号，如 AE001。 */
    private String entityNo;

    /**
     * OE 法定名称
     * <li>取自 OE Party 的机构资料；资料缺失时为 null。</li>
     */
    private String legalName;

    /** 经营国家，ISO 3166-1 alpha-2。 */
    private String operatingCountry;

    /** OE 状态 Code：PENDING、ACTIVE、SUSPENDED 或 CLOSED。 */
    private String status;

    /**
     * 当前有效来源类型 Code
     * <li>CLIENT 或 PARTNER；没有 ACTIVE 来源关系时为 null。</li>
     */
    private String sourceType;

    /** 收单资质 Code：MERCHANT、MERCHANT_RESALE_APPROVED 或 PAYFAC。 */
    private String acquiringQualification;

    /** 提供方；仅 PARTNER 来源有值，其他为 null。 */
    private ProviderSummary provider;

    /** 根修订号，修改时作为 expectedChangeVersion 回传；尚未建立审计根时为 1。 */
    private long changeVersion;

    /** 提供方摘要。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderSummary {
        /** Local Partner ID，十进制字符串。 */
        private String localPartnerId;

        /** Local Partner 法定名称；资料缺失时为 null。 */
        private String legalName;
    }
}
