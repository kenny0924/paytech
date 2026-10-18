package com.paytech.client.console.bff.service.model.response;

import java.util.List;

import com.paytech.compliance.api.MaterialRequirementsModels.RequirementBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一个材料主体及其材料要求；材料按资格类型通用，不按国家区分。 */
@Getter
@AllArgsConstructor
public class MaterialSubject {

    /** 主体 Party ID */
    private final String subjectPartyId;

    /** 资格类型：INDIVIDUAL_KYC、ORGANIZATION_KYB 或 RELATED_PERSON_KYC */
    private final String qualificationType;

    /**
     * 材料要求状态：AVAILABLE 或 UNAVAILABLE
     * <li>UNAVAILABLE 不得解释为无需材料。</li>
     */
    private final String requirementsStatus;

    /** 材料要求；不可用时为空列表 */
    private final List<RequirementBody> requirements;
}
