package com.paytech.commercial.test.builder;

import java.util.ArrayList;
import java.util.List;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.PricingProfileKind;
import com.paytech.commercial.api.pricing.request.CreatePricingProfileRequestBody;
import com.paytech.commercial.api.pricing.request.ProfileConditionEntry;
import com.paytech.commercial.api.pricing.request.UpdatePricingProfileRequestBody;

/**
 * 定价画像创建 / 修改请求体 Builder：默认 PAYMENT_METHOD / CARD，条件 CARD_BRAND=VISA 与 CURRENCY=USD
 * <li>不访问数据库；build() 不修复负例。</li>
 */
public final class PricingProfileBodyBuilder {
    /** 默认画像代码 */
    public static final String CODE = "B_CARD_VISA_USD";

    private String code = CODE;
    private String name = "Visa USD";
    private String kind = PricingProfileKind.PAYMENT_METHOD.getCode();
    private String paymentMethodCode = CommercialCodes.PaymentMethod.CARD;
    private final List<ProfileConditionEntry> conditions = new ArrayList<>(List.of(
        condition(PricingDimension.CARD_BRAND, CommercialCodes.CardBrand.VISA), condition(PricingDimension.CURRENCY, "USD")));

    private PricingProfileBodyBuilder() {
    }

    /** 合法默认值。 */
    public static PricingProfileBodyBuilder template() {
        return new PricingProfileBodyBuilder();
    }

    /** 一个条件。 */
    public static ProfileConditionEntry condition(PricingDimension dimension, String value) {
        ProfileConditionEntry entry = new ProfileConditionEntry();
        entry.setDimensionCode(dimension.getCode());
        entry.setConditionValue(value);
        return entry;
    }

    /** 设置画像代码。 */
    public PricingProfileBodyBuilder code(String value) {
        code = value;
        return this;
    }

    /** 设置名称。 */
    public PricingProfileBodyBuilder name(String value) {
        name = value;
        return this;
    }

    /** 设置种类。 */
    public PricingProfileBodyBuilder kind(PricingProfileKind value) {
        kind = value.getCode();
        return this;
    }

    /** 设置 Payment Method。 */
    public PricingProfileBodyBuilder paymentMethodCode(String value) {
        paymentMethodCode = value;
        return this;
    }

    /** 整体替换条件。 */
    public PricingProfileBodyBuilder conditions(ProfileConditionEntry... values) {
        conditions.clear();
        conditions.addAll(List.of(values));
        return this;
    }

    /** 创建请求体。 */
    public CreatePricingProfileRequestBody buildCreate() {
        CreatePricingProfileRequestBody body = new CreatePricingProfileRequestBody();
        body.setPricingProfileCode(code);
        body.setProfileName(name);
        body.setProfileKind(kind);
        body.setPaymentMethodCode(paymentMethodCode);
        body.setConditions(new ArrayList<>(conditions));
        return body;
    }

    /** 修改请求体。 */
    public UpdatePricingProfileRequestBody buildUpdate(long expectedVersion) {
        UpdatePricingProfileRequestBody body = new UpdatePricingProfileRequestBody();
        body.setExpectedChangeVersion(expectedVersion);
        body.setProfileName(name);
        body.setPaymentMethodCode(paymentMethodCode);
        body.setConditions(new ArrayList<>(conditions));
        return body;
    }
}
