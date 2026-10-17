package com.paytech.commerce.ext.rest.external.customerapi.v1.checkout;

import java.math.BigDecimal;
import java.util.List;

import com.paytech.commerce.customer.api.v1.checkout.response.GetCheckoutResponseBody;
import com.paytech.commerce.customer.api.v1.checkout.response.GetCheckoutResponseBody.*;
import com.paytech.commerce.domain.customer.CustomerCheckoutModels.ConsumerDetails;
import com.paytech.commerce.domain.customer.CustomerCheckoutModels.Seller;
import com.paytech.commerce.service.processor.customer.request.GetCheckoutRequest;
import com.paytech.commerce.service.processor.customer.response.GetCheckoutResponse;
import com.paytech.foundation.money.Money;

/** GET 入站模型转换；只投影允许公开的字段，不访问数据库或外部依赖。 */
public final class GetCheckoutConverter {
    /** 禁止实例化无状态转换器。 */
    private GetCheckoutConverter() {}

    /** 将原 Path 和 Query 组合为内部查询输入，不增加 GET Body。 */
    public static GetCheckoutRequest request(String checkoutToken, boolean includeConsumer) {
        return new GetCheckoutRequest(checkoutToken, includeConsumer);
    }

    /** 保持 GET 的原 JSON 字段、Code、金额精度和可空语义。 */
    public static GetCheckoutResponseBody response(GetCheckoutResponse result) {
        var amounts = result.getAmounts();
        var currency = result.getCurrency();
        return new GetCheckoutResponseBody(result.getOrderNo(), result.getOrderStatus().getCode(),
            result.getCheckoutStatus().getCode(),
            result.isReady() ? "READY" : "PENDING", result.getConsumerContractId() == null ? "ESTIMATED" : "FINAL",
            currency, result.getCountry(), result.getLocale(), result.getExpiresAt(),
            new CheckoutAmountsInfo(money(currency, amounts.getMerchandise()), money(currency, amounts.getDiscount()),
                money(currency, amounts.getNet()), money(currency, amounts.getTax()),
                money(currency, amounts.getRounding()), money(currency, amounts.getTotal())),
            result.getItems().stream().map(item -> new CheckoutItemInfo(item.getName(), item.getDescription(),
                item.getQuantity().stripTrailingZeros().toPlainString(), money(currency, item.getUnitAmount()),
                money(currency, item.getDiscountAmount()), money(currency, item.getNetAmount()))).toList(),
            new ConsumerInfo(result.isConsumerPresent() ? "COMPLETE" : "MISSING", details(result.getConsumerDetails())),
            seller(result.getSeller()), legal(result), payment(result), result.getAllowedActions(), result.getPollAfterMs(),
            new CardSavingInfo(result.isCardSavingAvailable()),
            result.getStoredCards().stream().map(card -> new StoredCardInfo(card.storedCardToken(), card.last4(),
                card.expiryMonth(), card.expiryYear())).toList());
    }

    /** 原值只在查询结果已包含显式回填时输出。 */
    static ConsumerDetailsInfo details(ConsumerDetails value) {
        if (value == null) {
            return null;
        }
        var address = value.billingAddress();
        return new ConsumerDetailsInfo(value.fullName(), value.email(), new BillingAddressInfo(address.countryCode(),
            address.addressLine1(), address.addressLine2(), address.region(), address.city(), address.postalCode()));
    }

    /** 按订单币种精度输出金额，不将尚未确定的税额或总额填为零。 */
    static String money(String currency, BigDecimal value) {
        return value == null ? null : Money.of(currency, value).toAmountString();
    }

    /** 卖方只来自原冻结公开披露，不暴露 OE 标识。 */
    static SellerInfo seller(Seller value) {
        return value == null ? null : new SellerInfo(value.legalName(), value.registrationNumber(),
            value.taxRegistrationNumber(), value.addressText(), value.supportEmail(), value.supportPhone());
    }

    /** 三份链接按原文件类型投影，尚未准备时整个 legal 为空。 */
    static LegalInfo legal(GetCheckoutResponse result) {
        if (result.getConsumerContractId() == null) {
            return null;
        }
        var links = result.getLinks();
        return new LegalInfo(Long.toString(result.getConsumerContractId()), List.of(
            new DocumentInfo("CONSUMER_TERMS", "Terms of sale", links.get(0).url(), links.get(0).expiresAt()),
            new DocumentInfo("REFUND_POLICY", "Refund policy", links.get(1).url(), links.get(1).expiresAt()),
            new DocumentInfo("PRIVACY_NOTICE", "Privacy notice", links.get(2).url(), links.get(2).expiresAt())));
    }

    /** 保留无 Intent、Payment 不可用和有明确支付结果三种状态。 */
    static PaymentInfo payment(GetCheckoutResponse result) {
        if (!result.isPaymentBound()) {
            return null;
        }
        var payment = result.getPayment();
        if (payment == null) {
            return new PaymentInfo("UNAVAILABLE", null, null, null, null, null, null, null);
        }
        return new PaymentInfo("AVAILABLE", payment.submissionId(), payment.submissionExpiresAt(), payment.attemptId(),
            payment.status(), payment.actionId() == null ? null
                : new NextActionInfo(payment.actionId(), payment.actionType(), payment.actionExpiresAt()),
            payment.failureCode() == null ? null : new FailureInfo(payment.failureCode(), payment.failureMessage()),
            payment.observedAt());
    }
}
