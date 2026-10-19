package com.paytech.commercial.auto.repository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import com.paytech.commercial.api.constant.CommercialCodes.BillingFactType;
import com.paytech.commercial.api.constant.CommercialCodes.Capability;
import com.paytech.commercial.api.constant.CommercialCodes.CardBrand;
import com.paytech.commercial.api.constant.CommercialCodes.FeeEvent;
import com.paytech.commercial.api.constant.CommercialCodes.PaymentMethod;
import com.paytech.commercial.api.constant.CommercialCodes.TermType;
import com.paytech.commercial.api.enums.CapabilityKind;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.ProductBusinessModeScope;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.api.enums.ProductStatus;
import com.paytech.commercial.core.dal.dataobject.Product;
import com.paytech.commercial.core.dal.dataobject.ProductCapability;
import com.paytech.commercial.core.dal.dataobject.ProductFeeEvent;
import com.paytech.commercial.domainservice.repository.ProductCapabilityRepository;
import com.paytech.commercial.domainservice.repository.ProductFeeEventRepository;
import com.paytech.commercial.domainservice.repository.ProductRepository;
import com.paytech.commercial.test.base.CommercialMockTestBase;
import com.paytech.commercial.test.builder.ProductBuilder;
import com.paytech.commercial.test.builder.ProductCapabilityBuilder;
import com.paytech.commercial.test.builder.ProductFeeEventBuilder;
import com.paytech.commercial.test.mocker.local.catalog.ProductCatalogMocker;
import com.paytech.foundation.common.error.ConcurrentRequestException;

/**
 * 产品目录三表的映射、唯一键与条件状态迁移：真实 MyBatis 与本地 mor_commercial，种子目录与初始化 DML 一致。
 * <li>本期没有生产写入方（目录随发版 DML 维护）；B / C / D 线以这些 Repository 读取目录。</li>
 *
 * @see ProductRepository#findAll()
 * @see ProductRepository#findByCode(ProductCode)
 * @see ProductRepository#updateStatus(Product, ProductStatus, Instant)
 * @see ProductCapabilityRepository#findByCodes(java.util.Collection)
 * @see ProductCapabilityRepository#findByProductCode(ProductCode)
 * @see ProductCapabilityRepository#updateStatus(ProductCapability, ProductStatus, Instant)
 * @see ProductFeeEventRepository#findByFeeEventAndTermType(String, String)
 * @see ProductFeeEventRepository#findByTermType(String)
 */
class ProductCatalogRepositoryTest extends CommercialMockTestBase {
    /** 状态迁移时点。 */
    private static final Instant LATER = Instant.parse("2026-09-12T08:30:00.123456Z");

    @Autowired
    private ProductCatalogMocker catalogMocker;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductFeeEventRepository feeEventRepository;

    @Autowired
    private ProductCapabilityRepository capabilityRepository;

    /** 种子目录插入后按主键、代码、产品与条款类型读回，字段与初始化 DML 一致。 */
    @Test
    void test_seedCatalogReadBack() {
        catalogMocker.mockSeedCatalog();

        List<Product> products = productRepository.findAll();
        Product tax = productRepository.findByCode(ProductCode.TAX_INVOICE);
        ProductCapability amex = capabilityRepository.findByCode(Capability.CARD_AMEX);
        ProductCapability payout = capabilityRepository.findByCode(Capability.PAYOUT_SWIFT);
        ProductFeeEvent chargeback = feeEventRepository.findByFeeEventAndTermType(FeeEvent.CHARGEBACK, TermType.DISPUTE_FEE);
        assertAll(() -> assertEquals(7, products.size()),
            () -> assertEquals(List.of(ProductCode.TAX_INVOICE, ProductCode.PLATFORM),
                products.stream().filter(Product::getBaseProduct).map(Product::getProductCode).toList()),
            () -> assertTrue(products.stream().allMatch(p -> p.getBusinessModeScope() == ProductBusinessModeScope.BOTH
                && p.getStatus() == ProductStatus.ACTIVE)),
            () -> assertEquals(tax.getProductId(), productRepository.findById(tax.getProductId()).getProductId()),
            () -> assertEquals(ProductCode.ACQUIRING, amex.getProductCode()),
            () -> assertEquals(CapabilityKind.PAYMENT_METHOD, amex.getCapabilityKind()),
            () -> assertEquals(PaymentMethod.CARD, amex.getPaymentMethodCode()),
            () -> assertEquals(PricingDimension.CARD_BRAND, amex.getDimensionCode()),
            () -> assertEquals(CardBrand.AMEX, amex.getDimensionValue()),
            () -> assertEquals(CapabilityKind.PAYOUT_RAIL, payout.getCapabilityKind()),
            () -> assertNull(payout.getPaymentMethodCode()), () -> assertNull(payout.getDimensionCode()),
            () -> assertEquals(6, capabilityRepository.findByProductCode(ProductCode.ACQUIRING).size()),
            () -> assertEquals(List.of(Capability.CARD_VISA, Capability.FX_SPOT),
                capabilityRepository.findByCodes(List.of(Capability.FX_SPOT, Capability.CARD_VISA, "UNKNOWN")).stream()
                    .map(ProductCapability::getCapabilityCode).toList()),
            () -> assertEquals(List.of(), capabilityRepository.findByCodes(List.of())),
            () -> assertEquals(ProductCode.DISPUTE, chargeback.getProductCode()),
            () -> assertEquals(BillingFactType.DISPUTE, chargeback.getBillingFactType()),
            () -> assertEquals(6, feeEventRepository.findByTermType(TermType.CLIENT_PRICING).size()),
            () -> assertEquals(3, feeEventRepository.findByProductCode(ProductCode.PLATFORM).size()),
            () -> assertEquals(List.of(), feeEventRepository.findByTermType(TermType.PARTNER_BUY_RATE)),
            () -> assertNull(feeEventRepository.findByFeeEventAndTermType(FeeEvent.TOKEN_STORED, TermType.PARTNER_SHARE)),
            () -> assertNull(productRepository.findById(1L)));
    }

    /** 产品代码、可开通项代码与（计费事件、条款类型）各自唯一。 */
    @Test
    void test_uniqueKeysRejectDuplicates() {
        catalogMocker.mockProduct(ProductBuilder.template());
        catalogMocker.mockCapability(ProductCapabilityBuilder.template());
        catalogMocker.mockFeeEvent(ProductFeeEventBuilder.template());

        assertAll(
            () -> assertThrows(DuplicateKeyException.class,
                () -> catalogMocker.mockProduct(ProductBuilder.template().productId(ProductBuilder.PRODUCT_ID + 1))),
            () -> assertThrows(DuplicateKeyException.class, () -> catalogMocker.mockCapability(
                ProductCapabilityBuilder.template().productCapabilityId(ProductCapabilityBuilder.PRODUCT_CAPABILITY_ID + 1))),
            () -> assertThrows(DuplicateKeyException.class, () -> catalogMocker.mockFeeEvent(ProductFeeEventBuilder.template()
                .productFeeEventId(ProductFeeEventBuilder.PRODUCT_FEE_EVENT_ID + 1).productCode(ProductCode.DISPUTE))),
            () -> assertEquals(1, productRepository.findAll().size()));
    }

    /** 以当前状态为条件迁移并同步对象；持有旧状态的对象再次迁移失败且不改库。 */
    @Test
    void test_statusTransitionIsConditional() {
        Product product = catalogMocker.mockProduct(ProductBuilder.template());
        ProductCapability capability = catalogMocker.mockCapability(ProductCapabilityBuilder.template());
        Product stale = productRepository.findById(ProductBuilder.PRODUCT_ID);
        ProductCapability staleCapability = capabilityRepository.findById(ProductCapabilityBuilder.PRODUCT_CAPABILITY_ID);

        productRepository.updateStatus(product, ProductStatus.RETIRED, LATER);
        capabilityRepository.updateStatus(capability, ProductStatus.RETIRED, LATER);

        Product read = productRepository.findById(ProductBuilder.PRODUCT_ID);
        ProductCapability readCapability = capabilityRepository.findById(ProductCapabilityBuilder.PRODUCT_CAPABILITY_ID);
        assertAll(() -> assertEquals(ProductStatus.RETIRED, product.getStatus()),
            () -> assertEquals(LATER, product.getUpdateTime()),
            () -> assertEquals(ProductStatus.RETIRED, read.getStatus()), () -> assertEquals(LATER, read.getUpdateTime()),
            () -> assertEquals(product.getCreateTime(), read.getCreateTime()),
            () -> assertEquals(ProductStatus.RETIRED, readCapability.getStatus()),
            () -> assertThrows(ConcurrentRequestException.class,
                () -> productRepository.updateStatus(stale, ProductStatus.DRAFT, LATER.plusSeconds(1))),
            () -> assertThrows(ConcurrentRequestException.class,
                () -> capabilityRepository.updateStatus(staleCapability, ProductStatus.DRAFT, LATER.plusSeconds(1))),
            () -> assertEquals(ProductStatus.ACTIVE, stale.getStatus()),
            () -> assertEquals(ProductStatus.RETIRED, productRepository.findById(ProductBuilder.PRODUCT_ID).getStatus()));
    }
}
