package com.paytech.commerce.test.builder.checkout;

import java.time.Instant;
import java.util.List;

import com.paytech.commerce.client.api.v1.checkout.request.CreateCheckoutRequestBody.CheckoutItemInfo;
import com.paytech.commerce.client.api.v1.checkout.request.CreateCheckoutRequestBody;

import lombok.Setter;
import lombok.experimental.Accessors;

/** 只构造请求；显式非法值和 null 原样交给 HTTP 入口验证。 */
@Setter
@Accessors(fluent = true)
public final class CreateCheckoutRequestBuilder {
    private String clientOrderId = "checkout-main";
    private String currency = "USD";
    private String consumerCountryCode = "US";
    private Instant expiresAt;
    private String consumerReference;
    private List<CheckoutItemInfo> items = List.of(
        item("line-1", "Premium subscription", "2", "19.95", "2.00"),
        item("line-2", "Setup service", "1", "5.00", null));

    /** 每次获得独立的合法请求模板。 */
    public static CreateCheckoutRequestBuilder template() {
        return new CreateCheckoutRequestBuilder();
    }

    /** 原样构造，不修复用例特意设置的非法字段。 */
    public CreateCheckoutRequestBody build() {
        return new CreateCheckoutRequestBody(clientOrderId, currency, consumerCountryCode, expiresAt, items,
            consumerReference);
    }

    /** 构造一条商品输入；不进行金额计算或资源访问。 */
    public static CheckoutItemInfo item(String id, String name, String quantity, String unit, String discount) {
        return new CheckoutItemInfo(id, name, null, "SERVICE", "STANDARD", quantity, unit, discount);
    }
}
