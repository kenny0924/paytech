package com.paytech.client.console.bff.integration.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.integration.CallerServiceCodes;
import com.paytech.client.console.bff.integration.ComplianceOnboardingClient;
import com.paytech.client.console.bff.service.model.response.ReviewRequirementError;
import com.paytech.client.console.bff.service.model.response.ReviewResult;
import com.paytech.compliance.api.ClientReviewResultApi;
import com.paytech.compliance.api.response.ClientReviewRequirementErrorBody;
import com.paytech.foundation.common.error.ServiceUnavailableException;

import lombok.AllArgsConstructor;

/**
 * 调用 Compliance 审核结果接口并校验回执
 * <li>结果必须与 client-master 申请状态一致：撤回的申请对应案件撤回或逾期（EXPIRED）。</li>
 * <li>需求级错误只接受大写代码与有界条数；不含内部理由、核验记录或决定人。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class ComplianceOnboardingClientImpl implements ComplianceOnboardingClient {
    /** 一次退回最多的需求级错误条数。 */
    static final int MAX_ERRORS = 200;

    /** 申请状态 → 可接受的案件结果。 */
    static final Map<String, Set<String>> OUTCOMES = Map.of(
        "RETURNED", Set.of("RETURNED"),
        "APPROVED", Set.of("APPROVED"),
        "REJECTED", Set.of("REJECTED"),
        "WITHDRAWN", Set.of("WITHDRAWN", "EXPIRED"));

    private static final String CODE = "[A-Z][A-Z0-9_]{0,63}";

    private final ClientReviewResultApi reviews;

    /** 读取审核结果并核对与申请状态一致。 */
    @Override
    public ReviewResult reviewResult(String applicationId, String status) {
        var value = reviews.result(CallerServiceCodes.CLIENT_CONSOLE_BFF, applicationId);
        if (value == null || !applicationId.equals(value.getApplicationId())
            || !OUTCOMES.getOrDefault(status, Set.of()).contains(value.getOutcome())) {
            throw new ServiceUnavailableException();
        }
        var errors = value.getErrors() == null ? List.<ClientReviewRequirementErrorBody>of() : value.getErrors();
        if (errors.size() > MAX_ERRORS || !errors.stream().allMatch(ComplianceOnboardingClientImpl::validError)) {
            throw new ServiceUnavailableException();
        }
        return new ReviewResult(value.getStatus(), value.getOutcome(), value.getRoundNo(), value.getPublicNote(),
            value.getDecidedAt(), value.getDueTime(), errors.stream().map(ComplianceOnboardingClientImpl::error).toList());
    }

    /** 需求键非空，错误码与补救码为大写代码。 */
    static boolean validError(ClientReviewRequirementErrorBody item) {
        return item != null && item.getRequirementKey() != null && !item.getRequirementKey().isBlank()
            && item.getCode() != null && item.getCode().matches(CODE)
            && (item.getRemediation() == null || item.getRemediation().matches(CODE));
    }

    /** 投影为页面模型。 */
    static ReviewRequirementError error(ClientReviewRequirementErrorBody item) {
        return new ReviewRequirementError(item.getRequirementKey(), item.getSubjectPartyId(), item.getCode(), item.getReason(),
            item.getRemediation(), item.getDueTime());
    }
}
