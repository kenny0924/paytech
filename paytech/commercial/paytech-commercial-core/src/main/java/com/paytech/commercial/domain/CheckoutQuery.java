package com.paytech.commercial.domain;

import static com.paytech.commercial.api.enums.CheckoutErrorCode.INVALID_CHECKOUT_QUERY;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.paytech.foundation.common.error.FailException;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;

/** 通过边界验证的内部查询；asOfText 保留调用方已冻结时点的原始 Wire 值。 */
@Value
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CheckoutQuery {
    /** 已授权的 Client 身份。 */
    @Positive
    long clientId;

    /** Commerce 原订单编号，完整解析必填。 */
    @NotBlank
    String orderNo;

    /** 已校验格式的消费者国家。 */
    @NotBlank
    String country;

    /** 已校验格式的交易币种。 */
    @NotBlank
    String currency;

    /**
     * 本次支付方式，可选
     * <li>给出时候选的 CLIENT 合同必须在该 OE 下至少命中一个 ENABLED 开通项；为空时不做开通项匹配。</li>
     */
    String paymentMethodCode;

    /** 法律文档语言，完整解析必填。 */
    @NotBlank
    String locale;

    /** Commerce 冻结的商业时点。 */
    @NotNull
    Instant asOf;

    /** CCT 开始检查的微秒时刻。 */
    @NotNull
    Instant evaluatedAt;

    /** 商业时点的原始协议文本，用于逐值回显。 */
    @NotBlank
    String asOfText;

    /** 解析规范身份、可选支付方式和 UTC 商业时点；不修改调用方的原始时间文本。 */
    public static CheckoutQuery resolve(String clientId, String orderNo, String country, String currency,
            String paymentMethodCode, String locale, String asOf, Instant now) {
        if (paymentMethodCode != null && !paymentMethodCode.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new FailException(INVALID_CHECKOUT_QUERY);
        }
        if (orderNo == null || !orderNo.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
                || locale == null || (locale.length() > 16 || !locale.matches("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*"))
                || asOf == null
                || !asOf.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,6})?Z")) {
            throw new FailException(INVALID_CHECKOUT_QUERY);
        }
        try {
            Instant time = Instant.parse(asOf);
            if (time.isAfter(now)) {
                throw new FailException(INVALID_CHECKOUT_QUERY);
            }
            return create(clientId, orderNo, country, currency, paymentMethodCode, locale, time, asOf, now);
        } catch (java.time.DateTimeException exception) {
            throw new FailException(INVALID_CHECKOUT_QUERY);
        }
    }

    /** 校验 Client 身份、国家和币种，拒绝溢出及非规范数字。 */
    private static CheckoutQuery create(String clientId, String orderNo, String country, String currency,
            String paymentMethodCode, String locale, Instant asOf, String asOfText, Instant now) {
        if (clientId == null || !clientId.matches("[1-9][0-9]{0,18}") || country == null
                || !country.matches("[A-Z]{2}") || currency == null || !currency.matches("[A-Z]{3}")) {
            throw new FailException(INVALID_CHECKOUT_QUERY);
        }
        try {
            return new CheckoutQuery(Long.parseLong(clientId), orderNo, country, currency, paymentMethodCode, locale, asOf,
                    now.truncatedTo(ChronoUnit.MICROS), asOfText);
        } catch (NumberFormatException exception) {
            throw new FailException(INVALID_CHECKOUT_QUERY);
        }
    }
}
