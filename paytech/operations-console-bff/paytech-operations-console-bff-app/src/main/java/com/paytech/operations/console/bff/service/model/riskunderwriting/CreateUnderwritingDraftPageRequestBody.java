package com.paytech.operations.console.bff.service.model.riskunderwriting;

import com.paytech.risk.decision.api.control.request.UnderwritingDecisionBody;

import jakarta.validation.Valid;

import lombok.Getter;
import lombok.Setter;

/** 页面建承保草稿：只发结论字段，输入快照由 BFF 从 Owner 重新读取。 */
@Getter
@Setter
public class CreateUnderwritingDraftPageRequestBody {
    /** 可选的初始结论字段。 */
    @Valid
    private UnderwritingDecisionBody decision;
}
