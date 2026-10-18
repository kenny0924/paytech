package com.paytech.ledger.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 批准或驳回人工调整请求。 */
@Getter
@Setter
public class ReviewManualAdjustmentRequestBody {
    /**
     * 复核人平台用户标识
     * <li>必须不同于编制人；未接统一身份，由内部调用方传入</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String checkerId;

    /**
     * 复核人确认的调整内容摘要
     * <li>必须等于提交时返回的 contentHash</li>
     */
    @NotBlank
    @Pattern(regexp = "[0-9a-f]{64}", message = "必须是 64 位小写十六进制")
    private String approvedContentHash;
}
