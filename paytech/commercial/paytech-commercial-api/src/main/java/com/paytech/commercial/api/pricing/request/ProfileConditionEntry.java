package com.paytech.commercial.api.pricing.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 定价画像的一个等值条件：同维度多行按 OR，异维度按 AND。 */
@Getter
@Setter
public class ProfileConditionEntry {
    /**
     * 定价维度
     * <li>须与画像种类相容：PAYMENT_METHOD 用卡类与交易维度，PAYOUT_RAIL 只用 PAYOUT_RAIL / DESTINATION_COUNTRY / CURRENCY，FX_PAIR 只用 CURRENCY_PAIR，DISPUTE 只用 DISPUTE_STAGE / CARD_BRAND / CURRENCY，PERIODIC 无条件。</li>
     */
    @NotBlank(message = "dimensionCode 必填")
    @Pattern(regexp = "CARD_BRAND|FUNDING_TYPE|CARD_LEVEL|CARD_PRODUCT|ISSUER_REGION|ISSUER_COUNTRY|THREE_DS_RESULT|CURRENCY|CONSUMER_COUNTRY|TRANSACTION_TYPE|PAYOUT_RAIL|DESTINATION_COUNTRY|CURRENCY_PAIR|DISPUTE_STAGE", message = "dimensionCode 只能为 CARD_BRAND、FUNDING_TYPE、CARD_LEVEL、CARD_PRODUCT、ISSUER_REGION、ISSUER_COUNTRY、THREE_DS_RESULT、CURRENCY、CONSUMER_COUNTRY、TRANSACTION_TYPE、PAYOUT_RAIL、DESTINATION_COUNTRY、CURRENCY_PAIR、DISPUTE_STAGE")
    private String dimensionCode;

    /**
     * 规范化值，大写字母数字及 _ . / -，1–128 位
     * <li>CURRENCY 为三位 ISO 4217，*_COUNTRY 为两位 ISO 3166-1。</li>
     */
    @NotBlank(message = "conditionValue 必填")
    @Pattern(regexp = "[A-Z0-9][A-Z0-9_./-]{0,127}", message = "conditionValue 必须是大写规范化值，最长 128 位")
    private String conditionValue;
}
