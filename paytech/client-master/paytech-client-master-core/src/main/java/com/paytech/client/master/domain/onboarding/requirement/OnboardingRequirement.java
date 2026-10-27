package com.paytech.client.master.domain.onboarding.requirement;

import java.time.Instant;

import com.paytech.client.master.api.enums.OnboardingAwaitingParty;
import com.paytech.client.master.api.enums.OnboardingRemediationCode;
import com.paytech.client.master.api.enums.OnboardingRequirementCode;
import com.paytech.client.master.api.enums.OnboardingRequirementKind;
import com.paytech.client.master.api.enums.OnboardingRequirementStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** 需求清单一项；实时计算，不持久化。 */
@Getter
@Setter
@AllArgsConstructor
public class OnboardingRequirement {
    /** 类别。 */
    private OnboardingRequirementKind kind;
    /** 需求键：Schema 字段键、group.<组>、address.<组件>、relation.<规则>、document.<材料槽>、declaration.<类型>。 */
    private String requirementKey;
    /** 所属主体 Party ID。 */
    private long subjectPartyId;
    /** 状态。 */
    private OnboardingRequirementStatus status;
    /** 等待的行动方。 */
    private OnboardingAwaitingParty awaitingActionFrom;
    /** 错误码；已满足为空。 */
    private OnboardingRequirementCode errorCode;
    /** 补救动作；已满足为空。 */
    private OnboardingRemediationCode remediationCode;
    /** 期限（退回给出的补件截止时间），可空。 */
    private Instant dueTime;

    /** 是否阻止正式提交。 */
    public boolean blocksSubmission() {
        return status == OnboardingRequirementStatus.CURRENTLY_DUE || status == OnboardingRequirementStatus.PAST_DUE;
    }
}
