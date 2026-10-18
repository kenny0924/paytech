package com.paytech.client.console.bff.integration.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.integration.CallerServiceCodes;
import com.paytech.client.console.bff.integration.ComplianceOnboardingClient;
import com.paytech.client.console.bff.service.model.response.ReviewResult;
import com.paytech.compliance.api.ClientReviewResultApi;
import com.paytech.compliance.api.MaterialRequirementsApi;
import com.paytech.compliance.api.MaterialRequirementsModels.RequirementBody;
import com.paytech.foundation.common.error.ServiceUnavailableException;

import lombok.AllArgsConstructor;

/**
 * 调用 Compliance 材料要求与审核结果接口并校验回执
 * <li>规则目录只接受真实、唯一且有界的条目；Program 身份不投影给浏览器。</li>
 * <li>审核结果不含内部理由、核验记录或决定人。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ComplianceOnboardingClientImpl implements ComplianceOnboardingClient {
    /** 一种资格类型最多的材料要求条数。 */
    static final int MAX_REQUIREMENTS = 1024;

    /** Risk 退回合同允许的分区。 */
    static final Set<String> RETURN_SECTIONS = Set.of("BASIC", "BUSINESS", "PEOPLE", "MATERIALS");

    private static final String CODE = "[A-Z][A-Z0-9_]{0,63}";

    private final ClientReviewResultApi reviews;
    private final MaterialRequirementsApi materialRequirements;

    /** 读取材料要求并校验条目与要求码唯一。 */
    @Override
    public List<RequirementBody> requirements(String qualificationType) {
        var value = materialRequirements.read(CallerServiceCodes.CLIENT_CONSOLE_BFF, qualificationType);
        if (value == null || value.items() == null || value.items().isEmpty() || value.items().size() > MAX_REQUIREMENTS) {
            throw new ServiceUnavailableException();
        }
        var codes = new HashSet<String>();
        for (var item : value.items()) {
            if (!validRequirement(item) || !codes.add(item.requirementCode())) {
                throw new ServiceUnavailableException();
            }
        }
        return value.items();
    }

    /** 读取审核结果并核对与 CCT 状态一致。 */
    @Override
    public ReviewResult reviewResult(String applicationId, String status) {
        var value = reviews.result(CallerServiceCodes.CLIENT_CONSOLE_BFF, applicationId);
        if (value == null || !applicationId.equals(value.getApplicationId()) || !status.equals(value.getOutcome())) {
            throw new ServiceUnavailableException();
        }
        if ("RETURNED".equals(status) && (value.getReturnSection() == null || !RETURN_SECTIONS.contains(value.getReturnSection()))) {
            throw new ServiceUnavailableException();
        }
        return new ReviewResult(value.getOutcome(), value.getReturnSection(), value.getPublicNote(), value.getDecidedAt());
    }

    /** 要求码与材料类型都是大写代码。 */
    static boolean validRequirement(RequirementBody item) {
        return item != null && item.requirementCode() != null && item.requirementCode().matches(CODE)
            && item.documentType() != null && item.documentType().matches(CODE);
    }
}
