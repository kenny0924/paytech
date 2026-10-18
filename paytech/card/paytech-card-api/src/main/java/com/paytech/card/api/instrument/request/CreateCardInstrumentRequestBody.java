package com.paytech.card.api.instrument.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建卡交易工具请求
 * <li>手输卡 card 与已存卡 storedCard 恰好提供一种；保存卡只用于手输卡且必须有 customerId，使用已存卡也必须有 customerId（Card 业务校验，400）。</li>
 */
@Getter
@Setter
public class CreateCardInstrumentRequestBody {
    /** Client ID，Payment 已按 Intent 核对 */
    @NotBlank(message = "Client ID 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "Client ID 必须为无前导零的正整数")
    @DecimalMax(value = "9223372036854775807", message = "Client ID 超出有效范围")
    private String clientId;

    /** Commerce Customer ID；访客支付不传 */
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "Customer ID 必须为无前导零的正整数")
    @DecimalMax(value = "9223372036854775807", message = "Customer ID 超出有效范围")
    private String customerId;

    /** Commerce 来源提交 ID：同一 Client 下的幂等键，重放返回同一工具 */
    @NotBlank(message = "来源提交 ID 必填")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "来源提交 ID 必须为无前导零的正整数")
    @DecimalMax(value = "9223372036854775807", message = "来源提交 ID 超出有效范围")
    private String sourceSubmissionId;

    /** 手输卡；与 storedCard 二选一 */
    @Valid
    private Card card;

    /** 已存卡与本次 CVV；与 card 二选一 */
    @Valid
    private StoredCard storedCard;

    /** 消费者是否同意支付成功后保存卡 */
    @NotNull(message = "保存意愿必填")
    private Boolean consentToSave;

    /**
     * 工具期限上限，ISO-8601 UTC
     * <li>Card 取它与卡有效期中较早者作为工具期限与 CVV 暂存期限，只能缩短，不能延长。</li>
     */
    @NotBlank(message = "工具期限上限必填")
    @Size(max = 40, message = "工具期限上限格式无效")
    private String expiresAt;

    /** 手输卡输入，仅用于本次受控处理 */
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

        /** 安全码，只以本卡 DEK 加密暂存 Redis，不落库、不哈希、不记录日志 */
        @NotBlank(message = "安全码必填")
        @Pattern(regexp = "[0-9]{3,4}", message = "安全码必须为 3–4 位数字")
        private String cvv;

        @Override
        public String toString() {
            return "Card[REDACTED]";
        }
    }

    /** 已存卡输入，仅用于本次受控处理 */
    @Getter
    @Setter
    public static class StoredCard {
        /** 对外已存卡 Token */
        @NotBlank(message = "已存卡 Token 必填")
        @Pattern(regexp = "sc_[A-Za-z0-9_-]{28}", message = "已存卡 Token 无效")
        private String storedCardToken;

        /** 本次输入的安全码 */
        @NotBlank(message = "安全码必填")
        @Pattern(regexp = "[0-9]{3,4}", message = "安全码必须为 3–4 位数字")
        private String cvv;

        @Override
        public String toString() {
            return "StoredCard[REDACTED]";
        }
    }

    @Override
    public String toString() {
        return "CreateCardInstrumentRequestBody[REDACTED]";
    }
}
