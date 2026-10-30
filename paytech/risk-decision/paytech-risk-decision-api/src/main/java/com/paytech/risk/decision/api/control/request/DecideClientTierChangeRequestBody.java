package com.paytech.risk.decision.api.control.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 审批或驳回档位变更申请（L6—L9）
 * <li>审批人不能是申请人；驳回时意见必填。</li>
 */
@Getter
@Setter
public class DecideClientTierChangeRequestBody {

    /** 期望的申请并发版本。 */
    @NotNull
    @Positive
    private Integer expectedVersion;

    /**
     * 审批意见，最长 500 字符
     * <li>驳回必填；批准可空。</li>
     */
    @Size(max = 500)
    private String comment;
}
