package com.paytech.operations.console.bff.service.model.riskunderwriting;

import com.paytech.risk.decision.api.control.request.UnderwritingDecisionBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** 页面保存承保草稿：期望版本与结论字段，输入快照由 BFF 重新读取。 */
@Getter
@Setter
public class SaveUnderwritingDraftPageRequestBody {
    /** 期望的草稿并发版本。 */
    @NotNull(message = "期望版本必填")
    @Positive(message = "期望版本须为正整数")
    private Integer expectedVersion;

    /** 结论字段。 */
    @Valid
    private UnderwritingDecisionBody decision;
}
