package com.paytech.commercial.test.builder;

import java.time.Instant;

import com.paytech.commercial.api.enums.ProductBusinessModeScope;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.core.dal.dataobject.Product;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;

/**
 * t_product 测试行的 Builder：template() 给出合法默认值，用例只覆盖与场景相关的字段。
 * <li>不访问数据库；可显式设置 null 或非法组合构造负例，build() 不修复输入。</li>
 */
public final class ProductBuilder {
    /** 默认产品目录行 ID */
    public static final long PRODUCT_ID = 920000000000001L;

    private final Product product = new Product();

    private ProductBuilder() {
    }

    /**
     * 可选、两种业务模式均可订阅、ACTIVE 的合成产品；产品代码默认 DATA_ANALYTICS，不与种子目录的 ID 冲突。
     *
     * @return 新 Builder
     */
    public static ProductBuilder template() {
        return new ProductBuilder()
            .productId(PRODUCT_ID)
            .productCode(ProductCode.DATA_ANALYTICS)
            .productName("Synthetic Product")
            .businessModeScope(ProductBusinessModeScope.BOTH)
            .baseProduct(Boolean.FALSE)
            .status(ProductStatus.ACTIVE)
            .createTime(CheckoutFixture.NOW)
            .updateTime(CheckoutFixture.NOW);
    }

    /** 设置产品目录行的内部唯一标识。 */
    public ProductBuilder productId(Long value) {
        product.setProductId(value);
        return this;
    }

    /** 设置可售产品模块的稳定代码，uk_product_code。 */
    public ProductBuilder productCode(ProductCode value) {
        product.setProductCode(value);
        return this;
    }

    /** 设置供 Operations 识别产品的展示名称。 */
    public ProductBuilder productName(String value) {
        product.setProductName(value);
        return this;
    }

    /** 设置该产品允许被哪种合同业务模式订阅。 */
    public ProductBuilder businessModeScope(ProductBusinessModeScope value) {
        product.setBusinessModeScope(value);
        return this;
    }

    /** 设置是否基座产品。 */
    public ProductBuilder baseProduct(Boolean value) {
        product.setBaseProduct(value);
        return this;
    }

    /** 设置生命周期状态。 */
    public ProductBuilder status(ProductStatus value) {
        product.setStatus(value);
        return this;
    }

    /** 设置记录创建的 UTC 微秒时间。 */
    public ProductBuilder createTime(Instant value) {
        product.setCreateTime(value);
        return this;
    }

    /** 设置记录最近一次持久化更新的 UTC 微秒时间。 */
    public ProductBuilder updateTime(Instant value) {
        product.setUpdateTime(value);
        return this;
    }

    /** 返回本 Builder 持有的对象；多次调用返回同一实例。 */
    public Product build() {
        return product;
    }
}
