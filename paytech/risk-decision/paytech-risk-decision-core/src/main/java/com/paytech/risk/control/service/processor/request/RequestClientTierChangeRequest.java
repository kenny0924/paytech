package com.paytech.risk.control.service.processor.request;

import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.control.domain.limittier.TierChecklist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 提交档位变更申请的 Owner 输入。 */
@Getter
@AllArgsConstructor
public class RequestClientTierChangeRequest {
    /** 申请人账号 ID。 */
    private final long actorAccountId;

    /** Client ID。 */
    @Positive
    private final long clientId;

    /** 调用方看到的档位并发版本。 */
    @Positive
    private final int expectedTierVersion;

    /** 目标档位。 */
    @NotNull
    private final LimitTier toTier;

    /** 变更理由。 */
    @NotBlank
    private final String reason;

    /** 上线复核说明；VERIFICATION → OBSERVATION 必填。 */
    private final String goLiveNote;

    /** 观察期目标天数；可空，缺省见申请规则。 */
    private final Integer observationTargetDays;

    /** 观察期目标放行笔数；可空，缺省见申请规则。 */
    private final Integer observationTargetTransactions;

    /** 上线复核清单；VERIFICATION → OBSERVATION 必填。 */
    private final TierChecklist checklist;
}
