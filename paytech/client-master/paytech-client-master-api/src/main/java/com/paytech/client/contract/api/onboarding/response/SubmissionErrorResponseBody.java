package com.paytech.client.contract.api.onboarding.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 一项需求级提交错误（CM-9）；前端按 code 与 remediation 取 onboardingSchema 文案，不展示 message。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionErrorResponseBody {
    /**
     * 需求键
     * <li>Schema 字段键（如 individual.givenName、attributes.AE_TRN）、group.<二选一组>、address.<组件>、relation.<规则>、
     * document.<材料槽>、declaration.<声明类型>、documents[<材料引用 ID>]、people。</li>
     */
    private String requirementKey;
    /** 所属主体 Party ID；主主体为申请主体，关联人员为该人员。 */
    private String subjectPartyId;
    /** 错误码，取值见 OnboardingRequirementCode。 */
    private String code;
    /** 补救动作，取值见 OnboardingRemediationCode。 */
    private String remediation;
    /** 英文过渡说明（code/remediation），保留一个版本，Web 不展示。 */
    private String message;
}
