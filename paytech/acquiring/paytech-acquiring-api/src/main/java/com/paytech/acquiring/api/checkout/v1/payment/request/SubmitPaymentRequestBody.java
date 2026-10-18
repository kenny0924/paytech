package com.paytech.acquiring.api.checkout.v1.payment.request;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 一次提交当前合同确认与卡信息；卡输入仅用于本次支付，不进入日志或本地持久化。 */
@Getter
@Setter
public class SubmitPaymentRequestBody {
    /** 对当前页面已展示合同的明确确认。 */
    @Valid
    @NotNull
    private LegalConfirmationInfo legalConfirmation;

    /** 本次支付手输的卡信息；与 storedCard 二选一。 */
    @Valid
    private CardInfo card;

    /** 本次支付使用的已存卡与重新输入的 CVV；与 card 二选一。 */
    @Valid
    private StoredCardInfo storedCard;

    /** 是否同意支付成功后保存卡；只用于手输卡，仅在 Checkout 查询返回 cardSaving.available 时接受。 */
    private Boolean saveCard;

    /** 手输卡与已存卡恰好提供一种。 */
    @JsonIgnore
    @AssertTrue(message = "卡信息与已存卡必须且只能提供一种")
    public boolean isSingleCardSource() {
        return (card == null) != (storedCard == null);
    }

    /** 已存卡不能再次选择保存。 */
    @JsonIgnore
    @AssertTrue(message = "使用已存卡时不能选择保存卡")
    public boolean isSaveOnlyForNewCard() {
        return !(Boolean.TRUE.equals(saveCard) && storedCard != null);
    }

    /** 已存卡输入；CVV 每次重新输入，只用于本次支付。 */
    @Getter
    @Setter
    public static class StoredCardInfo {
        /** Checkout 查询返回的已存卡 Token。 */
        @NotBlank
        @Size(max = 64)
        private String storedCardToken;

        /** 本次输入的安全码。 */
        @NotBlank
        @Pattern(regexp = "[0-9]{3,4}")
        private String cvv;

        @Override
        public String toString() {
            return "StoredCardInfo[REDACTED]";
        }
    }

    /** 冻结合同的确认信息。 */
    @Getter
    @Setter
    public static class LegalConfirmationInfo {
        /** 当前 Checkout 已展示的合同编号，正十进制字符串。 */
        @NotBlank
        @Pattern(regexp = "[1-9][0-9]{0,18}")
        private String consumerContractId;

        /** 用户本次点击支付表示同意，必须为 true。 */
        @NotNull
        @AssertTrue
        private Boolean confirmed;
    }

    /** 卡字段不生成包含属性的 toString，不进入命令摘要或补偿事件。 */
    @Getter
    @Setter
    public static class CardInfo {
        /** 卡号，仅接受 12 至 19 位数字。 */
        @NotBlank
        @Pattern(regexp = "[0-9]{12,19}")
        private String pan;

        /** 两位到期月份，01 至 12。 */
        @NotBlank
        @Pattern(regexp = "0[1-9]|1[0-2]")
        private String expiryMonth;

        /** 四位到期年份，卡有效性由 Payment 判断。 */
        @NotBlank
        @Pattern(regexp = "[0-9]{4}")
        private String expiryYear;

        /** 持卡人姓名，不接受控制字符。 */
        @NotBlank
        @Size(max = 128)
        @Pattern(regexp = "[^\\p{Cc}]*")
        private String cardholderName;

        /** 三位或四位安全码，不持久化、不参与任何摘要。 */
        @NotBlank
        @Pattern(regexp = "[0-9]{3,4}")
        private String cvv;
    }
}
