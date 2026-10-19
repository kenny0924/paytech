package com.paytech.commercial.test.mocker.local.catalog;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.commercial.core.dal.dataobject.Product;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;
import com.paytech.commercial.core.dal.dataobject.ProductFeeEvent;
import com.paytech.commercial.domainservice.repository.ProductCapabilityRepository;
import com.paytech.commercial.domainservice.repository.ProductFeeEventRepository;
import com.paytech.commercial.domainservice.repository.ProductRepository;
import com.paytech.commercial.test.builder.ProductBuilder;
import com.paytech.commercial.test.builder.ProductCapabilityBuilder;
import com.paytech.commercial.test.builder.ProductFeeEventBuilder;

import lombok.AllArgsConstructor;

/**
 * 产品目录前置数据：经正式 Repository 插入种子目录或单行，供 B / C / D 线用例使用。
 * <li>无可变状态；插入的行由 CommercialDataCleaner 在每例结束后统一清理。</li>
 */
@TestComponent
@AllArgsConstructor
public class ProductCatalogMocker {
    private final ProductRepository productRepository;
    private final ProductFeeEventRepository productFeeEventRepository;
    private final ProductCapabilityRepository productCapabilityRepository;

    /**
     * 插入与初始化 DML 相同的完整种子目录（7 个产品、11 个可开通项、21 个计费事件）。
     *
     * @return 已插入的目录
     */
    public ProductCatalogFixture mockSeedCatalog() {
        ProductCatalogFixture catalog = ProductCatalogFixture.load();
        catalog.products().forEach(productRepository::insert);
        catalog.capabilities().forEach(productCapabilityRepository::insert);
        catalog.feeEvents().forEach(productFeeEventRepository::insert);
        return catalog;
    }

    /**
     * 插入一个产品。
     *
     * @param builder 产品 Builder
     * @return 已插入的产品
     */
    public Product mockProduct(ProductBuilder builder) {
        Product product = builder.build();
        productRepository.insert(product);
        return product;
    }

    /**
     * 插入一个可开通项。
     *
     * @param builder 可开通项 Builder
     * @return 已插入的可开通项
     */
    public ProductCapability mockCapability(ProductCapabilityBuilder builder) {
        ProductCapability capability = builder.build();
        productCapabilityRepository.insert(capability);
        return capability;
    }

    /**
     * 插入一个计费事件。
     *
     * @param builder 计费事件 Builder
     * @return 已插入的计费事件
     */
    public ProductFeeEvent mockFeeEvent(ProductFeeEventBuilder builder) {
        ProductFeeEvent feeEvent = builder.build();
        productFeeEventRepository.insert(feeEvent);
        return feeEvent;
    }
}
