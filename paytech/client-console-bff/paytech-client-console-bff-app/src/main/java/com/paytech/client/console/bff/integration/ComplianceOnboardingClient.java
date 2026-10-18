package com.paytech.client.console.bff.integration;

import java.util.List;

import com.paytech.client.console.bff.service.model.response.ReviewResult;
import com.paytech.compliance.api.MaterialRequirementsModels.RequirementBody;

/** Compliance 入驻材料要求与客户可见审核结果的出站端口。 */
public interface ComplianceOnboardingClient {

    /**
     * 读取一种资格类型的通用材料要求。
     *
     * @param qualificationType INDIVIDUAL_KYC、ORGANIZATION_KYB 或 RELATED_PERSON_KYC
     * @return 非空、有界且要求码唯一的材料要求；否则按依赖不可用抛出
     */
    List<RequirementBody> requirements(String qualificationType);

    /**
     * 读取申请最近一次审核的客户可见结果。
     *
     * @param applicationId 申请 ID
     * @param status CCT 当前申请状态（RETURNED、APPROVED 或 REJECTED）
     * @return 与 CCT 状态一致的审核结果；不一致说明投影尚未同步，按依赖不可用抛出
     */
    ReviewResult reviewResult(String applicationId, String status);
}
