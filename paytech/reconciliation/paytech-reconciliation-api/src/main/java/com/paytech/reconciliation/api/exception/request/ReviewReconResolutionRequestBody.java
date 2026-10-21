package com.paytech.reconciliation.api.exception.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 复核异常处置请求；复核人取自 X-Gateway-Actor-Subject。 */
@Getter
@Setter
public class ReviewReconResolutionRequestBody {
    /**
     * 复核决定
     * <li>APPROVED：同一事务应用；REJECTED：异常回到 OPEN。</li>
     */
    @NotBlank
    @Pattern(regexp = "APPROVED|REJECTED", message = "只能为 APPROVED 或 REJECTED")
    private String decision;

    /**
     * 复核人确认的处置内容摘要
     * <li>必须等于提交时返回的 contentHash。</li>
     */
    @NotBlank
    @Pattern(regexp = "[0-9a-f]{64}", message = "必须是 64 位小写十六进制")
    private String approvedContentHash;
}
