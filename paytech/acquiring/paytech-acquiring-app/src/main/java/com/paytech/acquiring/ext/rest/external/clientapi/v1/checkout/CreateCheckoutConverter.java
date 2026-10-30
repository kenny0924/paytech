package com.paytech.acquiring.ext.rest.external.clientapi.v1.checkout;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.paytech.acquiring.api.client.v1.checkout.request.CreateCheckoutRequestBody;
import com.paytech.acquiring.api.client.v1.checkout.request.CreateCheckoutRequestBody.CheckoutItemInfo;
import com.paytech.acquiring.api.client.v1.checkout.response.CreateCheckoutResponseBody;
import com.paytech.acquiring.api.client.v1.checkout.response.CreateCheckoutResponseBody.AmountsInfo;
import com.paytech.acquiring.service.processor.checkout.request.CreateCheckoutRequest;
import com.paytech.acquiring.service.processor.checkout.request.CreateCheckoutRequest.Item;
import com.paytech.acquiring.service.processor.checkout.response.CreateCheckoutResponse;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.money.Money;

/** Checkout Client API 传输模型与应用模型之间的纯转换。 */
public final class CreateCheckoutConverter {

    /** 纯协议转换工具不需要实例。 */
    private CreateCheckoutConverter() {}

    /** 将已校验 HTTP 字段转为单一业务请求。 */
    public static CreateCheckoutRequest toRequest(long clientId,
        CreateCheckoutRequestBody body) {
        return new CreateCheckoutRequest(clientId, body.getClientOrderId(), body.getCurrency(),
            body.getConsumerCountryCode(), body.getExpiresAt(),
            body.getItems().stream().map(CreateCheckoutConverter::toRequestItem).toList(), body.getConsumerReference());
    }

    /** 将不可变创建回执映射为公开 Wire DTO。 */
    public static CreateCheckoutResponseBody toResponse(CreateCheckoutResponse result) {
        CreateCheckoutResponse value = Objects.requireNonNull(result, "Create Checkout Result 不能为空");
        AmountsInfo amounts = new AmountsInfo(formatMoney(value.merchandiseAmount(), value.currency()),
            formatMoney(value.discountAmount(), value.currency()), formatMoney(value.netAmount(), value.currency()),
            optionalMoney(value.taxAmount(), value.currency()), optionalMoney(value.roundingAmount(), value.currency()),
            optionalMoney(value.totalAmount(), value.currency()), null);
        return new CreateCheckoutResponseBody(value.orderNo(), value.clientOrderId(), value.checkoutUrl(),
            value.currency(), amounts, value.expiresAt(), "READY", value.amountStatus().getCode(), "OPEN",
            value.taxBlockReason());
    }

    /** 验证并返回创建响应唯一允许的相对 Order Location。 */
    public static URI toLocation(CreateCheckoutResponse result) {
        CreateCheckoutResponse value = Objects.requireNonNull(result, "Create Checkout Result 不能为空");
        String expected = "/api/v1/orders/" + value.orderNo();
        String location = value.location();
        if (!expected.equals(location) || !StandardCharsets.US_ASCII.newEncoder().canEncode(location)) {
            throw new ErrorException("Create Checkout Location 与公开 Order 坐标不一致");
        }
        try {
            URI uri = new URI(location);
            if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || !location.equals(uri.getRawPath()) || location.contains("\\")) {
                throw new ErrorException("Create Checkout Location 必须是安全相对路径");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new ErrorException("Create Checkout Location 格式无效", exception);
        }
    }

    /** 保留原商品顺序与省略字段，规范化由请求语义处理完成。 */
    private static Item toRequestItem(CheckoutItemInfo item) {
        return new Item(item.getClientItemId(), item.getName(), item.getDescription(), item.getProductTypeCode(),
            item.getTaxCategoryCode(), item.getQuantity(), item.getUnitAmount(), item.getDiscountAmount());
    }

    /** 未确定的金额仍返回 null。 */
    private static String optionalMoney(BigDecimal value, String currency) {
        return value == null ? null : formatMoney(value, currency);
    }

    /** 按当前公开币种精度输出金额字符串。 */
    private static String formatMoney(BigDecimal amount, String currency) {
        BigDecimal value = Objects.requireNonNull(amount, "Amount 不能为空");
        if (value.signum() < 0) {
            throw new ErrorException("Create Checkout Result 金额不能为负数");
        }
        try {
            return Money.of(currency, value).toAmountString();
        } catch (InvalidParameterException exception) {
            throw new ErrorException("Create Checkout Result 金额不符合币种精度", exception);
        }
    }
}
