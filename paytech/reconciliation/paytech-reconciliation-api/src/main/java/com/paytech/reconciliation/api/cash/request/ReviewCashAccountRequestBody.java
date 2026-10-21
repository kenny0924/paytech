package com.paytech.reconciliation.api.cash.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 复核银行账户新建或关闭申请请求；复核人取自 X-Gateway-Actor-Subject。 */
@Getter
@Setter
public class ReviewCashAccountRequestBody {
    /**
     * 复核决定
     * <li>APPROVED 或 REJECTED。拒绝新建账户时账户 CLOSED；拒绝关闭申请时清空申请。</li>
     */
    @NotBlank
    @Pattern(regexp = "APPROVED|REJECTED", message = "只能为 APPROVED 或 REJECTED")
    private String decision;

    /**
     * 复核人确认的内容摘要
     * <li>必须等于最近一次提交返回的 contentHash。</li>
     */
    @NotBlank
    @Pattern(regexp = "[0-9a-f]{64}", message = "必须是 64 位小写十六进制")
    private String approvedContentHash;
}
