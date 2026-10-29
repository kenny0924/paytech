package com.paytech.operations.console.bff.service.model.riskunderwriting;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 页面预览：Client 与结算周期建议；输入快照由 BFF 按 Client 重新读取。 */
@Getter
@Setter
public class UnderwritingPreviewPageRequestBody {
    /** 被承保 Client ID。 */
    @NotBlank(message = "Client ID 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "Client ID 格式无效")
    private String clientId;

    /** 结算周期建议 T+N 的 N，0—90；为空按 0 计。 */
    @Min(value = 0, message = "结算周期建议不小于 0")
    @Max(value = 90, message = "结算周期建议不大于 90")
    private Integer suggestedSettlementDays;
}
