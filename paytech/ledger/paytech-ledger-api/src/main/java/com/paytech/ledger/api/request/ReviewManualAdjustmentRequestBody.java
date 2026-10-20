package com.paytech.ledger.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 批准或驳回人工调整请求；复核人取自 X-Gateway-Actor-Subject。 */
@Getter
@Setter
public class ReviewManualAdjustmentRequestBody {
    /**
     * 复核人确认的调整内容摘要
     * <li>必须等于提交时返回的 contentHash</li>
     */
    @NotBlank
    @Pattern(regexp = "[0-9a-f]{64}", message = "必须是 64 位小写十六进制")
    private String approvedContentHash;
}
