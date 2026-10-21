package com.paytech.commercial.test.mocker.local.catalog;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.paytech.commercial.api.enums.CapabilityKind;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.ProductBusinessModeScope;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.core.dal.dataobject.Product;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;
import com.paytech.commercial.core.dal.dataobject.ProductCapabilityDimension;
import com.paytech.commercial.core.dal.dataobject.ProductFeeEvent;
import com.paytech.commercial.test.mocker.local.checkout.CheckoutFixture;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.utils.EnumUtils;

import tools.jackson.databind.json.JsonMapper;

/**
 * 种子产品目录（fixtures/commercial/product-catalog.json）的只读加载，内容与初始化 DML commercial-initial-data.sql 一致。
 * <li>只构造 DataObject，不访问数据库；创建与更新时间使用固定测试时点。未知 Code 直接失败，不映射为 null。</li>
 *
 * @param products 7 个产品
 * @param capabilities 9 个可开通项
 * @param feeEvents 21 个计费事件
 * @param dimensions 20 个可配维度
 */
public record ProductCatalogFixture(List<Product> products, List<ProductCapability> capabilities,
    List<ProductFeeEvent> feeEvents, List<ProductCapabilityDimension> dimensions) {
    /** 测试资源路径。 */
    public static final String RESOURCE = "/fixtures/commercial/product-catalog.json";

    /**
     * 读取种子目录；每次返回新对象，用例可自由修改。
     *
     * @return 种子目录
     */
    @SuppressWarnings("unchecked")
    public static ProductCatalogFixture load() {
        Map<String, List<Map<String, Object>>> rows;
        try (var input = ProductCatalogFixture.class.getResourceAsStream(RESOURCE)) {
            rows = JsonMapper.builder().build().readValue(input, Map.class);
        } catch (java.io.IOException exception) {
            throw new AssertionError("无法读取种子产品目录: " + RESOURCE, exception);
        }
        return new ProductCatalogFixture(rows.get("product").stream().map(ProductCatalogFixture::product).toList(),
            rows.get("product_capability").stream().map(ProductCatalogFixture::capability).toList(),
            rows.get("product_fee_event").stream().map(ProductCatalogFixture::feeEvent).toList(),
            rows.get("product_capability_dimension").stream().map(ProductCatalogFixture::dimension).toList());
    }

    private static Product product(Map<String, Object> row) {
        Product product = new Product();
        product.setProductId(((Number) row.get("product_id")).longValue());
        product.setProductCode(code(ProductCode.class, row.get("product_code")));
        product.setProductName((String) row.get("product_name"));
        product.setBusinessModeScope(code(ProductBusinessModeScope.class, row.get("business_mode_scope")));
        product.setBaseProduct(((Number) row.get("base_product")).intValue() == 1);
        product.setStatus(code(ProductStatus.class, row.get("status")));
        product.setCreateTime(CheckoutFixture.NOW);
        product.setUpdateTime(CheckoutFixture.NOW);
        return product;
    }

    private static ProductCapability capability(Map<String, Object> row) {
        ProductCapability capability = new ProductCapability();
        capability.setProductCapabilityId(((Number) row.get("product_capability_id")).longValue());
        capability.setProductCode(code(ProductCode.class, row.get("product_code")));
        capability.setCapabilityCode((String) row.get("capability_code"));
        capability.setCapabilityName((String) row.get("capability_name"));
        capability.setCapabilityKind(code(CapabilityKind.class, row.get("capability_kind")));
        capability.setPaymentMethodCode((String) row.get("payment_method_code"));
        capability.setStatus(code(ProductStatus.class, row.get("status")));
        capability.setChangeVersion(1L);
        capability.setCreateTime(CheckoutFixture.NOW);
        capability.setUpdateTime(CheckoutFixture.NOW);
        return capability;
    }

    private static ProductFeeEvent feeEvent(Map<String, Object> row) {
        ProductFeeEvent feeEvent = new ProductFeeEvent();
        feeEvent.setProductFeeEventId(((Number) row.get("product_fee_event_id")).longValue());
        feeEvent.setProductCode(code(ProductCode.class, row.get("product_code")));
        feeEvent.setFeeEventCode((String) row.get("fee_event_code"));
        feeEvent.setTermType((String) row.get("term_type"));
        feeEvent.setBillingFactType((String) row.get("billing_fact_type"));
        feeEvent.setCapabilityKind(row.get("capability_kind") == null ? null
            : code(CapabilityKind.class, row.get("capability_kind")));
        feeEvent.setCapabilityCode((String) row.get("capability_code"));
        feeEvent.setChangeVersion(1L);
        feeEvent.setCreateTime(CheckoutFixture.NOW);
        feeEvent.setUpdateTime(CheckoutFixture.NOW);
        return feeEvent;
    }

    private static ProductCapabilityDimension dimension(Map<String, Object> row) {
        ProductCapabilityDimension dimension = new ProductCapabilityDimension();
        dimension.setCapabilityDimensionId(((Number) row.get("capability_dimension_id")).longValue());
        dimension.setCapabilityCode((String) row.get("capability_code"));
        dimension.setDimensionCode(code(PricingDimension.class, row.get("dimension_code")));
        dimension.setRequired(((Number) row.get("required")).intValue() == 1);
        dimension.setCreateTime(CheckoutFixture.NOW);
        dimension.setUpdateTime(CheckoutFixture.NOW);
        return dimension;
    }

    private static <T extends CodeEnum> T code(Class<T> type, Object value) {
        return Objects.requireNonNull(EnumUtils.getEnumByCode(type, (String) value),
            () -> "种子目录含未知 " + type.getSimpleName() + ": " + value);
    }
}
