package com.paytech.client.console.bff.service.processor;

import java.util.Set;

import com.paytech.compliance.api.enums.MaterialQualificationType;

/** 入驻页面共用的客户类型与资格类型对应；材料要求只由资格类型决定，不按国家区分。 */
final class OnboardingQualifications {
    /** 组织客户类型。 */
    static final String ORGANIZATION = "ORGANIZATION";

    /** 客户类型的合同值。 */
    static final Set<String> CLIENT_TYPES = Set.of("INDIVIDUAL", ORGANIZATION);

    private OnboardingQualifications() { }

    /** 主主体的资格类型：组织为 KYB，个人为 KYC。 */
    static String primary(String clientType) {
        var type = ORGANIZATION.equals(clientType) ? MaterialQualificationType.ORGANIZATION_KYB
            : MaterialQualificationType.INDIVIDUAL_KYC;
        return type.getCode();
    }
}
