package com.paytech.risk.decision.api.control.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 建承保草稿的请求。 */
@Getter
@Setter
public class CreateUnderwritingDraftRequestBody {

    /** operations-console-bff 组装的输入快照。 */
    @NotNull
    @Valid
    private UnderwritingInputBody input;

    /** 可选的初始结论字段；缺省时结论字段为空。 */
    @Valid
    private UnderwritingDecisionBody decision;
}
