package com.paytech.payment.execution.service.processor.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** 支付受理请求 */
@Getter
@Setter
public class PayRequest {
    /** 支付意图 ID */
    @NotNull(message = "intentId 必填")
    @Positive(message = "intentId 必须为正整数")
    private Long intentId;

    /** Client ID */
    @NotNull(message = "clientId 必填")
    @Positive(message = "clientId 必须为正整数")
    private Long clientId;

    /**
     * 来源提交 ID
     * <li>更换支付工具时使用新的提交 ID。</li>
     */
    @NotNull(message = "sourceSubmissionId 必填")
    @Positive(message = "sourceSubmissionId 必须为正整数")
    private Long sourceSubmissionId;

    /**
     * 捕获模式
     * <li>AUTOMATIC：自动收款；MANUAL：授权后等待显式捕获。</li>
     * <li>受理后不可修改。</li>
     */
    @NotBlank(message = "捕获模式必填")
    @Pattern(regexp = "MANUAL|AUTOMATIC", message = "captureMode 只能为 MANUAL 或 AUTOMATIC")
    private String captureMode;

    /** 支付方式与工具 */
    @Valid
    @NotNull(message = "支付方式与工具必填")
    private PayMethod payMethod;

    /** 支付方式与工具 */
    @Getter
    @Setter
    public static class PayMethod {
        /** 支付方式代码 */
        @NotBlank(message = "支付方式必填")
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "支付方式代码无效")
        private String paymentMethodCode;

        /**
         * 支付工具 Token
         * <li>限定本次 Client、Intent 和来源提交；重复提交必须使用原值。</li>
         */
        @NotBlank(message = "支付工具 Token 必填")
        @Pattern(regexp = "[A-Za-z0-9_-]{1,64}", message = "支付工具 Token 格式无效")
        private String paymentInstrumentToken;

        @Override
        public String toString() {
            return "PayMethod[REDACTED]";
        }
    }

    @Override
    public String toString() {
        return "PayRequest[REDACTED]";
    }
}
