package com.paytech.payout.api.beneficiary.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 运营查看收款账户原值请求（P10）。 */
@Getter
@Setter
public class RevealBeneficiaryAccountRequestBody {
    /** 查看人 Operations 账号 ID */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须为规范正整数字符串")
    private String actorId;

    /**
     * 查看原因
     * <li>MAKER_REVIEW 或 CHECKER_REVIEW（BeneficiaryRevealReason），记入 REVEAL 事件。</li>
     */
    @NotBlank
    @Pattern(regexp = "MAKER_REVIEW|CHECKER_REVIEW", message = "只能为 MAKER_REVIEW 或 CHECKER_REVIEW")
    private String reasonCode;
}
