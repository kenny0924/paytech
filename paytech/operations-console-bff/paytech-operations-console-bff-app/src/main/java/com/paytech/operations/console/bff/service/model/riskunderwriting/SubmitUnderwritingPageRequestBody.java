package com.paytech.operations.console.bff.service.model.riskunderwriting;

import com.paytech.risk.decision.api.control.request.UnderwritingDecisionBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 页面提交承保：期望版本、结论字段与提交结论；输入快照由 BFF 重新读取后随提交冻结。 */
@Getter
@Setter
public class SubmitUnderwritingPageRequestBody {
    /** 期望的草稿并发版本。 */
    @NotNull(message = "期望版本必填")
    @Positive(message = "期望版本须为正整数")
    private Integer expectedVersion;

    /** 结论字段。 */
    @NotNull(message = "结论字段必填")
    @Valid
    private UnderwritingDecisionBody decision;

    /** 提交结论：APPROVE 或 DECLINE（HIGH 风险只能 APPROVE，即提交复核）。 */
    @NotBlank(message = "提交结论必填")
    @Pattern(regexp = "APPROVE|DECLINE", message = "提交结论只能为 APPROVE 或 DECLINE")
    private String outcome;

    /** 拒绝原因；DECLINE 时必填（由 Risk 校验）。 */
    @Size(max = 500, message = "拒绝原因不超过 500 个字符")
    private String declineReason;
}
