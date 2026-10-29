package com.paytech.risk.decision.api.control.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 自动承保判定与估算敞口预览的请求；纯计算，不写库。 */
@Getter
@Setter
public class UnderwritingPreviewRequestBody {

    /** operations-console-bff 组装的输入快照。 */
    @NotNull
    @Valid
    private UnderwritingInputBody input;

    /** 结算周期建议 T+N 的 N，0—90；为空按 0 计。 */
    @Min(0)
    @Max(90)
    private Integer suggestedSettlementDays;
}
