package com.paytech.commercial.test.builder;

import java.time.Instant;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.core.dal.dataobject.ProductFeeEvent;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;

/**
 * t_product_fee_event 测试行的 Builder：template() 给出合法默认值，用例只覆盖与场景相关的字段。
 * <li>不访问数据库；可显式设置 null 或非法组合构造负例，build() 不修复输入。</li>
 */
public final class ProductFeeEventBuilder {
    /** 默认计费事件目录行 ID */
    public static final long PRODUCT_FEE_EVENT_ID = 920000000000201L;

    private final ProductFeeEvent productFeeEvent = new ProductFeeEvent();

    private ProductFeeEventBuilder() {
    }

    /**
     * ACQUIRING 的 SALE 事件在 CLIENT_PRICING 下收费，对应 billing PAYMENT 事实。
     *
     * @return 新 Builder
     */
    public static ProductFeeEventBuilder template() {
        return new ProductFeeEventBuilder()
            .productFeeEventId(PRODUCT_FEE_EVENT_ID)
            .productCode(ProductCode.ACQUIRING)
            .feeEventCode(CommercialCodes.FeeEvent.SALE)
            .termType(CommercialCodes.TermType.CLIENT_PRICING)
            .billingFactType(CommercialCodes.BillingFactType.PAYMENT)
            .createTime(CheckoutFixture.NOW)
            .updateTime(CheckoutFixture.NOW);
    }

    /** 设置计费事件目录行的内部唯一标识。 */
    public ProductFeeEventBuilder productFeeEventId(Long value) {
        productFeeEvent.setProductFeeEventId(value);
        return this;
    }

    /** 设置所属产品的稳定代码。 */
    public ProductFeeEventBuilder productCode(ProductCode value) {
        productFeeEvent.setProductCode(value);
        return this;
    }

    /** 设置触发收费的业务动作代码。 */
    public ProductFeeEventBuilder feeEventCode(String value) {
        productFeeEvent.setFeeEventCode(value);
        return this;
    }

    /** 设置该事件可收费的商业条款类型。 */
    public ProductFeeEventBuilder termType(String value) {
        productFeeEvent.setTermType(value);
        return this;
    }

    /** 设置对应的 billing 事实类型代码。 */
    public ProductFeeEventBuilder billingFactType(String value) {
        productFeeEvent.setBillingFactType(value);
        return this;
    }

    /** 设置记录创建的 UTC 微秒时间。 */
    public ProductFeeEventBuilder createTime(Instant value) {
        productFeeEvent.setCreateTime(value);
        return this;
    }

    /** 设置记录最近一次持久化更新的 UTC 微秒时间。 */
    public ProductFeeEventBuilder updateTime(Instant value) {
        productFeeEvent.setUpdateTime(value);
        return this;
    }

    /** 返回本 Builder 持有的对象；多次调用返回同一实例。 */
    public ProductFeeEvent build() {
        return productFeeEvent;
    }
}
