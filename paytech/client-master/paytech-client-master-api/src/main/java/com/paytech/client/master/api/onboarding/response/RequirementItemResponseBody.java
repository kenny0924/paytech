package com.paytech.client.master.api.onboarding.response;

import lombok.Getter;
import lombok.Setter;

/** 需求清单一项（CM-7）；前端按 onboardingSchema.error / remediation / requirementStatus 取文案。 */
@Getter
@Setter
public class RequirementItemResponseBody {
    /** 类别：FIELD、IDENTIFIER、PERSON、RELATION、DOCUMENT、DECLARATION。 */
    private String requirementKind;

    /**
     * 需求键
     * <li>Schema 字段键、group.<二选一组>、address.<组件>、relation.<规则>、document.<材料槽>、declaration.<类型>、documents[<材料 ID>]、people。</li>
     */
    private String requirementKey;

    /** 所属主体 Party ID。 */
    private String subjectPartyId;

    /** 状态：CURRENTLY_DUE、EVENTUALLY_DUE、PAST_DUE、PENDING_VERIFICATION、SATISFIED、WAIVED。 */
    private String status;

    /** 等待的行动方：CLIENT、OPERATIONS、SYSTEM；已满足为空。 */
    private String awaitingActionFrom;

    /** 错误码（OnboardingRequirementCode）；已满足为空。 */
    private String errorCode;

    /** 补救动作（OnboardingRemediationCode）；已满足为空。 */
    private String remediationCode;

    /** 退回补件截止 UTC 时间，可空。 */
    private String dueTime;
}
