package com.paytech.operations.console.bff.service.model.payout.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 运营查看收款账户原值的浏览器请求；查看人取当前 Operations 会话。 */
@Getter
@Setter
public class PayoutRevealRequestBody {
    /**
     * 查看原因
     * <li>MAKER_REVIEW 或 CHECKER_REVIEW（BeneficiaryRevealReason），记入 payout 的 REVEAL 事件。</li>
     */
    @NotBlank
    @Pattern(regexp = "MAKER_REVIEW|CHECKER_REVIEW", message = "只能为 MAKER_REVIEW 或 CHECKER_REVIEW")
    private String reasonCode;
}
