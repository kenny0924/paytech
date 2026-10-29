package com.paytech.risk.decision.api.control.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** 保存承保草稿的请求：整体替换草稿的输入与结论字段。 */
@Getter
@Setter
public class SaveUnderwritingDraftRequestBody {

    /** 期望的草稿并发版本，与详情返回的 version 一致。 */
    @NotNull
    @Positive
    private Integer expectedVersion;

    /** operations-console-bff 重新读取的输入快照。 */
    @NotNull
    @Valid
    private UnderwritingInputBody input;

    /** 结论字段；缺省时清空草稿结论。 */
    @Valid
    private UnderwritingDecisionBody decision;
}
