package com.paytech.client.master.api.onboarding.response;

import lombok.Getter;
import lombok.Setter;

/** CM-1 可入驻辖区。 */
@Getter
@Setter
public class JurisdictionResponseBody {
    /** 辖区代码。 */
    private String jurisdictionCode;

    /** ISO 3166-1 alpha-2 国家代码。 */
    private String countryCode;

    /** 展示名称文案键（onboardingSchema.jurisdiction.<代码>）。 */
    private String labelKey;

    /** 准入策略：OPEN 或 ENHANCED_ONLY（CLOSED 不返回）。 */
    private String acceptancePolicy;

    /** 准入受限说明的文案键，可空。 */
    private String acceptanceReasonKey;
}
