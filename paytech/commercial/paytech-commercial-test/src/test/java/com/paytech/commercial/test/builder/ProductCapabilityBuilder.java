package com.paytech.commercial.test.builder;

import java.time.Instant;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.CapabilityKind;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;

/**
 * t_product_capability 测试行的 Builder：template() 给出合法默认值，用例只覆盖与场景相关的字段。
 * <li>不访问数据库；可显式设置 null 或非法组合构造负例，build() 不修复输入。</li>
 */
public final class ProductCapabilityBuilder {
    /** 默认可开通项目录行 ID */
    public static final long PRODUCT_CAPABILITY_ID = 920000000000101L;

    private final ProductCapability productCapability = new ProductCapability();

    private ProductCapabilityBuilder() {
    }

    /**
     * ACQUIRING 下 ACTIVE 的 Visa 卡开通项（PAYMENT_METHOD / CARD，维度 CARD_BRAND=VISA）。
     *
     * @return 新 Builder
     */
    public static ProductCapabilityBuilder template() {
        return new ProductCapabilityBuilder()
            .productCapabilityId(PRODUCT_CAPABILITY_ID)
            .productCode(ProductCode.ACQUIRING)
            .capabilityCode(CommercialCodes.Capability.CARD_VISA)
            .capabilityName("Visa")
            .capabilityKind(CapabilityKind.PAYMENT_METHOD)
            .paymentMethodCode(CommercialCodes.PaymentMethod.CARD)
            .dimensionCode(PricingDimension.CARD_BRAND)
            .dimensionValue(CommercialCodes.CardBrand.VISA)
            .status(ProductStatus.ACTIVE)
            .createTime(CheckoutFixture.NOW)
            .updateTime(CheckoutFixture.NOW);
    }

    /** 设置可开通项目录行的内部唯一标识。 */
    public ProductCapabilityBuilder productCapabilityId(Long value) {
        productCapability.setProductCapabilityId(value);
        return this;
    }

    /** 设置所属产品的稳定代码。 */
    public ProductCapabilityBuilder productCode(ProductCode value) {
        productCapability.setProductCode(value);
        return this;
    }

    /** 设置全局唯一且稳定的可开通项代码，uk_product_capability_code。 */
    public ProductCapabilityBuilder capabilityCode(String value) {
        productCapability.setCapabilityCode(value);
        return this;
    }

    /** 设置供 Operations 识别可开通项的展示名称。 */
    public ProductCapabilityBuilder capabilityName(String value) {
        productCapability.setCapabilityName(value);
        return this;
    }

    /** 设置能力种类，决定是否绑定 Payment Method。 */
    public ProductCapabilityBuilder capabilityKind(CapabilityKind value) {
        productCapability.setCapabilityKind(value);
        return this;
    }

    /** 设置Payment 拥有的 Payment Method 稳定代码。 */
    public ProductCapabilityBuilder paymentMethodCode(String value) {
        productCapability.setPaymentMethodCode(value);
        return this;
    }

    /** 设置把开通项细分到画像维度的单一条件维度。 */
    public ProductCapabilityBuilder dimensionCode(PricingDimension value) {
        productCapability.setDimensionCode(value);
        return this;
    }

    /** 设置细分维度的单个规范化值，如 AMEX。 */
    public ProductCapabilityBuilder dimensionValue(String value) {
        productCapability.setDimensionValue(value);
        return this;
    }

    /** 设置生命周期状态。 */
    public ProductCapabilityBuilder status(ProductStatus value) {
        productCapability.setStatus(value);
        return this;
    }

    /** 设置记录创建的 UTC 微秒时间。 */
    public ProductCapabilityBuilder createTime(Instant value) {
        productCapability.setCreateTime(value);
        return this;
    }

    /** 设置记录最近一次持久化更新的 UTC 微秒时间。 */
    public ProductCapabilityBuilder updateTime(Instant value) {
        productCapability.setUpdateTime(value);
        return this;
    }

    /** 返回本 Builder 持有的对象；多次调用返回同一实例。 */
    public ProductCapability build() {
        return productCapability;
    }
}
