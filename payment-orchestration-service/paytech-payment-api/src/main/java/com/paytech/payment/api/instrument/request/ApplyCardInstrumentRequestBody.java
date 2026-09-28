package com.paytech.payment.api.instrument.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 申请卡支付工具请求 */
@Getter
@Setter
public class ApplyCardInstrumentRequestBody {
    /** Client ID */
    @NotBlank(message = "Client ID 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "Client ID 必须为无前导零的正整数")
    @DecimalMax(value = "9223372036854775807", message = "Client ID 超出有效范围")
    private String clientId;

    /** 支付意图 ID */
    @NotBlank(message = "支付意图 ID 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "支付意图 ID 必须为无前导零的正整数")
    @DecimalMax(value = "9223372036854775807", message = "支付意图 ID 超出有效范围")
    private String intentId;

    /**
     * 来源提交 ID
     * <li>与后续 pay 使用同一个值；换卡时使用新的提交 ID。</li>
     */
    @NotBlank(message = "来源提交 ID 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "来源提交 ID 必须为无前导零的正整数")
    @DecimalMax(value = "9223372036854775807", message = "来源提交 ID 超出有效范围")
    private String sourceSubmissionId;

    /** 精确支付方式代码 */
    @NotBlank(message = "支付方式必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "支付方式代码无效")
    private String paymentMethodCode;

    /** 卡信息 */
    @Valid
    @NotNull(message = "卡信息必填")
    private Card card;

    /** 卡输入，仅用于本次受控处理 */
    @Getter
    @Setter
    public static class Card {
        /** 卡号 */
        @NotBlank(message = "卡号必填")
        @Pattern(regexp = "[0-9]{12,19}", message = "卡号必须为 12–19 位数字")
        private String pan;

        /** 卡有效期月份，01–12 */
        @NotBlank(message = "卡有效期月份必填")
        @Pattern(regexp = "0[1-9]|1[0-2]", message = "卡有效期月份无效")
        private String expiryMonth;

        /** 卡有效期年份，四位数字 */
        @NotBlank(message = "卡有效期年份必填")
        @Pattern(regexp = "[1-9][0-9]{3}", message = "卡有效期年份无效")
        private String expiryYear;

        /** 持卡人姓名 */
        @NotBlank(message = "持卡人姓名必填")
        @Size(max = 128, message = "持卡人姓名最多 128 位")
        @Pattern(regexp = "[^\\p{Cc}]+", message = "持卡人姓名不能包含控制字符")
        private String cardholderName;

        /** 安全码，仅供本次处理，不保存或记录日志 */
        @NotBlank(message = "安全码必填")
        @Pattern(regexp = "[0-9]{3,4}", message = "安全码必须为 3–4 位数字")
        private String cvv;

        @Override
        public String toString() {
            return "Card[REDACTED]";
        }
    }

    @Override
    public String toString() {
        return "ApplyCardInstrumentRequestBody[REDACTED]";
    }
}
