package com.paytech.client.master.service.processor;

import java.util.List;

import com.paytech.client.contract.api.onboarding.response.SubmissionErrorResponseBody;
import com.paytech.client.master.core.dal.dataobject.ClientDraftIdentity;
import com.paytech.client.master.domain.onboarding.requirement.OnboardingRequirement;
import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;

/**
 * 需求级错误的两种投影：提交核对的 typed errors[]，以及正式提交 422/409 Problem 的 errors[{field,message}]
 * <li>Problem 字段：主主体为需求键，关联人员为 people[partyId].需求键；message 为 code/remediation，供 BFF 解析，不含客户资料。</li>
 */
public final class SubmissionErrors {
    /** 仅静态投影。 */
    private SubmissionErrors() {
    }

    /** 提交核对与需求清单的 typed 错误。 */
    public static List<SubmissionErrorResponseBody> bodies(List<OnboardingRequirement> errors) {
        return errors.stream().map(item -> new SubmissionErrorResponseBody(item.getRequirementKey(),
            Long.toString(item.getSubjectPartyId()), item.getErrorCode().getCode(), item.getRemediationCode().getCode(),
            item.getErrorCode().getCode() + "/" + item.getRemediationCode().getCode())).toList();
    }

    /**
     * 正式提交被拒的 Problem
     *
     * @param code SUBMISSION_INCOMPLETE（422）或 IDENTITY_CONFLICT（409）
     * @param identity 申请，用于区分主主体与关联人员
     * @param errors 阻止提交的需求项
     * @return 带 errors[] 的业务异常
     */
    static RequestBodyValidationException rejection(CodeMessageEnum code, ClientDraftIdentity identity,
        List<OnboardingRequirement> errors) {
        return new RequestBodyValidationException(code, errors.stream().map(item -> new ProblemFieldError(
            (item.getSubjectPartyId() == identity.partyId() ? "" : "people[" + item.getSubjectPartyId() + "].")
                + item.getRequirementKey(),
            item.getErrorCode().getCode() + "/" + item.getRemediationCode().getCode())).toList(), null);
    }
}
