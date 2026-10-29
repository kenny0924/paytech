package com.paytech.risk.decision.api.control.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 提交承保草稿的请求：冻结输入快照并给出结论
 * <li>复核级别非 HIGH：APPROVE 直接 APPROVED，DECLINE 须原因并 DECLINED。</li>
 * <li>复核级别为 HIGH：只能 APPROVE，进入 SUBMITTED 等待风控负责人复核。</li>
 */
@Getter
@Setter
public class SubmitUnderwritingRequestBody {

    /** 期望的草稿并发版本。 */
    @NotNull
    @Positive
    private Integer expectedVersion;

    /** operations-console-bff 重新读取的输入快照，提交时冻结。 */
    @NotNull
    @Valid
    private UnderwritingInputBody input;

    /** 结论字段，提交必填项见字段说明。 */
    @NotNull
    @Valid
    private UnderwritingDecisionBody decision;

    /** 提交结论：APPROVE 或 DECLINE。 */
    @NotBlank
    @Pattern(regexp = "APPROVE|DECLINE")
    private String outcome;

    /** 拒绝原因，最长 500 字符；outcome=DECLINE 时必填。 */
    @Size(max = 500)
    private String declineReason;
}
