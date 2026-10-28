package com.paytech.commercial.auto.repository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import com.paytech.commercial.api.constant.CommercialCodes.Capability;
import com.paytech.commercial.api.enums.AgreementCapabilityExecutionStatus;
import com.paytech.commercial.api.enums.AgreementCapabilityStatus;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.ProductCode;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;
import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;
import com.paytech.commercial.core.dal.dataobject.AgreementProduct;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityLimitRepository;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityRepository;
import com.paytech.commercial.domainservice.repository.AgreementProductRepository;
import com.paytech.commercial.test.base.CommercialMockTestBase;
import com.paytech.commercial.test.builder.AgreementCapabilityBuilder;
import com.paytech.commercial.test.builder.AgreementProductBuilder;
import com.paytech.commercial.test.dal.CommercialTestMapper;
import com.paytech.commercial.test.mocker.local.agreement.AgreementSubscriptionMocker;
import com.paytech.foundation.common.error.ConcurrentRequestException;

/**
 * 合同订阅、开通项与约束三表的映射、唯一键、按父读取、删除与条件更新：真实 MyBatis 与本地 mor_commercial。
 * <li>本期没有生产写入方（B 线的合同写入 Processor 尚未实现）；不建 FK，订阅与开通项不要求合同行存在。</li>
 *
 * @see AgreementProductRepository#insert(AgreementProduct)
 * @see AgreementProductRepository#findByAgreementAndProduct(long, ProductCode)
 * @see AgreementProductRepository#findByAgreementIds(java.util.Collection)
 * @see AgreementProductRepository#delete(AgreementProduct)
 * @see AgreementCapabilityRepository#findByUniqueKey(long, String, String)
 * @see AgreementCapabilityRepository#findByAgreementIds(java.util.Collection)
 * @see AgreementCapabilityRepository#updateMembership(AgreementCapability, AgreementCapabilityStatus,
 *      AgreementCapabilityExecutionStatus, Instant)
 * @see AgreementCapabilityRepository#updateExecutionStatus(AgreementCapability, AgreementCapabilityExecutionStatus, Instant)
 * @see AgreementCapabilityRepository#findAgreementIdsByExecutionStatus(java.util.Collection, long, int, int, int)
 * @see AgreementCapabilityRepository#findAgreementIdsByEntityNo(String)
 * @see AgreementCapabilityRepository#updateTerms(AgreementCapability, Instant, Instant, BigDecimal, Integer, Instant)
 * @see AgreementCapabilityLimitRepository#insertAll(List)
 * @see AgreementCapabilityLimitRepository#findByAgreementCapabilityIds(java.util.Collection)
 * @see AgreementCapabilityLimitRepository#deleteByAgreementCapabilityId(long)
 */
class AgreementSubscriptionRepositoryTest extends CommercialMockTestBase {
    /** 合成合同。 */
    private static final long AGREEMENT_ID = AgreementProductBuilder.AGREEMENT_ID;

    /** 更新时点（含微秒以下精度，验证截断）。 */
    private static final Instant LATER = Instant.parse("2026-09-12T08:30:00.123456789Z");

    @Autowired
    private CommercialTestMapper testMapper;

    @Autowired
    private AgreementSubscriptionMocker subscriptionMocker;

    @Autowired
    private AgreementProductRepository productRepository;

    @Autowired
    private AgreementCapabilityRepository capabilityRepository;

    @Autowired
    private AgreementCapabilityLimitRepository limitRepository;

    /** 订阅按唯一键与按合同读取；同合同同产品重复插入被唯一键拒绝；删除后再次删除报并发变化。 */
    @Test
    void test_productSubscriptionLifecycle() {
        AgreementProduct acquiring = subscriptionMocker.mockProduct(AgreementProductBuilder.template());
        subscriptionMocker.mockProduct(AgreementProductBuilder.template()
            .agreementProductId(AgreementProductBuilder.AGREEMENT_PRODUCT_ID + 1).productCode(ProductCode.FX));

        AgreementProduct read = productRepository.findByAgreementAndProduct(AGREEMENT_ID, ProductCode.ACQUIRING);
        assertAll(() -> assertEquals(acquiring.getAgreementProductId(), read.getAgreementProductId()),
            () -> assertEquals(acquiring.getCreateTime(), read.getCreateTime()),
            () -> assertEquals(List.of(ProductCode.ACQUIRING, ProductCode.FX),
                productRepository.findByAgreementId(AGREEMENT_ID).stream().map(AgreementProduct::getProductCode).toList()),
            () -> assertEquals(2, productRepository.findByAgreementIds(List.of(AGREEMENT_ID, 1L)).size()),
            () -> assertEquals(List.of(), productRepository.findByAgreementIds(List.of())),
            () -> assertNull(productRepository.findByAgreementAndProduct(AGREEMENT_ID, ProductCode.PAYOUT)),
            () -> assertThrows(DuplicateKeyException.class, () -> subscriptionMocker.mockProduct(
                AgreementProductBuilder.template().agreementProductId(AgreementProductBuilder.AGREEMENT_PRODUCT_ID + 2))));

        productRepository.delete(acquiring);

        assertAll(() -> assertNull(productRepository.findById(acquiring.getAgreementProductId())),
            () -> assertEquals(1, testMapper.countAgreementProducts(AGREEMENT_ID)),
            () -> assertThrows(ConcurrentRequestException.class, () -> productRepository.delete(acquiring)));
    }

    /** 开通项与约束按唯一键、按父集合读取，约束按主键升序；整体删除返回行数；空列表不访问数据库。 */
    @Test
    void test_capabilityWithLimitsReadBack() {
        var mocker = subscriptionMocker.mockCapability(AgreementCapabilityBuilder.template()
            .rollingReserveRate(new BigDecimal("0.1000000000")).rollingReserveDays(90))
            .currencies("USD", "AED").limit(PricingDimension.CONSUMER_COUNTRY, "AE");
        subscriptionMocker.mockCapability(AgreementCapabilityBuilder.template()
            .agreementCapabilityId(AgreementCapabilityBuilder.AGREEMENT_CAPABILITY_ID + 1).capabilityCode(Capability.APPLE_PAY))
            .currencies("USD");
        long capabilityId = mocker.capability().getAgreementCapabilityId();

        AgreementCapability read = capabilityRepository.findByUniqueKey(AGREEMENT_ID, AgreementCapabilityBuilder.ENTITY_NO,
            Capability.CARD);
        List<AgreementCapabilityLimit> limits = limitRepository.findByAgreementCapabilityId(capabilityId);
        assertAll(() -> assertEquals(capabilityId, read.getAgreementCapabilityId()),
            () -> assertEquals(AgreementCapabilityStatus.ACTIVE, read.getStatus()),
            () -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED, read.getExecutionStatus()),
            () -> assertEquals(AgreementCapabilityBuilder.EFFECTIVE_FROM, read.getEffectiveFrom()),
            () -> assertNull(read.getEffectiveTo()),
            () -> assertEquals(0, new BigDecimal("0.1").compareTo(read.getRollingReserveRate())),
            () -> assertEquals(90, read.getRollingReserveDays()),
            () -> assertEquals(2, capabilityRepository.findByAgreementId(AGREEMENT_ID).size()),
            () -> assertEquals(2, capabilityRepository.findByAgreementIds(List.of(AGREEMENT_ID)).size()),
            () -> assertEquals(List.of(), capabilityRepository.findByAgreementIds(List.of())),
            () -> assertNull(capabilityRepository.findByUniqueKey(AGREEMENT_ID, "AE992", Capability.CARD)),
            () -> assertEquals(List.of("USD", "AED", "AE"),
                limits.stream().map(AgreementCapabilityLimit::getLimitValue).toList()),
            () -> assertEquals(List.of(PricingDimension.CURRENCY, PricingDimension.CURRENCY,
                PricingDimension.CONSUMER_COUNTRY), limits.stream().map(AgreementCapabilityLimit::getLimitCode).toList()),
            () -> assertEquals(4, limitRepository.findByAgreementCapabilityIds(
                List.of(capabilityId, capabilityId + 1)).size()),
            () -> assertEquals(List.of(), limitRepository.findByAgreementCapabilityIds(List.of())),
            () -> assertThrows(DuplicateKeyException.class, () -> mocker.limit(PricingDimension.CURRENCY, "USD")));

        limitRepository.insertAll(List.of());
        int deleted = limitRepository.deleteByAgreementCapabilityId(capabilityId);

        assertAll(() -> assertEquals(3, deleted), () -> assertEquals(0, testMapper.countCapabilityLimits(capabilityId)),
            () -> assertEquals(1, testMapper.countCapabilityLimits(capabilityId + 1)));
    }

    /** 成员资格关闭清空执行状态、重新纳入写入新执行状态；重查 Job 的分片游标分页与按 OE 定位合同只返回符合条件的合同 ID。 */
    @Test
    void test_capabilityMembershipAndAgreementLookups() {
        long base = AgreementCapabilityBuilder.AGREEMENT_CAPABILITY_ID;
        AgreementCapability pending = subscriptionMocker.mockCapability(AgreementCapabilityBuilder.template()
            .executionStatus(AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES)).capability();
        subscriptionMocker.mockCapability(AgreementCapabilityBuilder.template().agreementCapabilityId(base + 1)
            .agreementId(AGREEMENT_ID + 1).executionStatus(AgreementCapabilityExecutionStatus.SUSPENDED_BY_PREREQUISITE));
        subscriptionMocker.mockCapability(AgreementCapabilityBuilder.template().agreementCapabilityId(base + 2)
            .agreementId(AGREEMENT_ID + 2).entityNo("AE992"));
        subscriptionMocker.mockCapability(AgreementCapabilityBuilder.template().agreementCapabilityId(base + 3)
            .agreementId(AGREEMENT_ID + 3).executionStatus(AgreementCapabilityExecutionStatus.SUSPENDED_MANUALLY));
        Set<AgreementCapabilityExecutionStatus> awaiting = Set.of(AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES,
            AgreementCapabilityExecutionStatus.SUSPENDED_BY_PREREQUISITE);

        assertAll(() -> assertEquals(List.of(AGREEMENT_ID, AGREEMENT_ID + 1),
                capabilityRepository.findAgreementIdsByExecutionStatus(awaiting, 0L, 0, 1, 10)),
            () -> assertEquals(List.of(AGREEMENT_ID),
                capabilityRepository.findAgreementIdsByExecutionStatus(awaiting, 0L, 0, 1, 1)),
            () -> assertEquals(List.of(AGREEMENT_ID + 1),
                capabilityRepository.findAgreementIdsByExecutionStatus(awaiting, AGREEMENT_ID, 0, 1, 10)),
            () -> assertEquals(List.of(AGREEMENT_ID + Math.floorMod(-AGREEMENT_ID, 2)),
                capabilityRepository.findAgreementIdsByExecutionStatus(awaiting, 0L, 0, 2, 10)),
            () -> assertEquals(List.of(AGREEMENT_ID + 2), capabilityRepository.findAgreementIdsByEntityNo("AE992")),
            () -> assertEquals(List.of(AGREEMENT_ID, AGREEMENT_ID + 1, AGREEMENT_ID + 3),
                capabilityRepository.findAgreementIdsByEntityNo(AgreementCapabilityBuilder.ENTITY_NO)));

        AgreementCapability stale = capabilityRepository.findById(pending.getAgreementCapabilityId());
        capabilityRepository.updateMembership(pending, AgreementCapabilityStatus.DISABLED, null, LATER);
        AgreementCapability disabled = capabilityRepository.findById(pending.getAgreementCapabilityId());
        assertAll(() -> assertEquals(AgreementCapabilityStatus.DISABLED, disabled.getStatus()),
            () -> assertNull(disabled.getExecutionStatus()), () -> assertNull(pending.getExecutionStatus()),
            () -> assertThrows(ConcurrentRequestException.class, () -> capabilityRepository.updateExecutionStatus(pending,
                AgreementCapabilityExecutionStatus.ENABLED, LATER)),
            () -> assertThrows(ConcurrentRequestException.class, () -> capabilityRepository.updateMembership(stale,
                AgreementCapabilityStatus.DISABLED, null, LATER)));

        capabilityRepository.updateMembership(pending, AgreementCapabilityStatus.ACTIVE,
            AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES, LATER);
        assertEquals(AgreementCapabilityExecutionStatus.PENDING_PREREQUISITES,
            capabilityRepository.findById(pending.getAgreementCapabilityId()).getExecutionStatus());
    }

    /** 执行状态与成员资格以当前状态为条件、条款以 update_time 为条件更新并同步对象；陈旧对象更新失败且不改库。 */
    @Test
    void test_capabilityConditionalUpdates() {
        AgreementCapability capability = subscriptionMocker.mockCapability(AgreementCapabilityBuilder.template()).capability();
        AgreementCapability stale = capabilityRepository.findById(capability.getAgreementCapabilityId());
        Instant effectiveTo = Instant.parse("2027-01-01T00:00:00Z");

        capabilityRepository.updateExecutionStatus(capability, AgreementCapabilityExecutionStatus.SUSPENDED_MANUALLY, LATER);
        capabilityRepository.updateTerms(capability, AgreementCapabilityBuilder.EFFECTIVE_FROM, effectiveTo,
            new BigDecimal("0.05"), 30, true, LATER.plusSeconds(1));

        AgreementCapability read = capabilityRepository.findById(capability.getAgreementCapabilityId());
        assertAll(() -> assertEquals(AgreementCapabilityExecutionStatus.SUSPENDED_MANUALLY, read.getExecutionStatus()),
            () -> assertEquals(effectiveTo, read.getEffectiveTo()),
            () -> assertEquals(0, new BigDecimal("0.05").compareTo(read.getRollingReserveRate())),
            () -> assertEquals(30, read.getRollingReserveDays()),
            () -> assertEquals(Boolean.TRUE, read.getRequire3ds()),
            () -> assertEquals(capability.getUpdateTime(), read.getUpdateTime()),
            () -> assertEquals(Instant.parse("2026-09-12T08:30:01.123456Z"), read.getUpdateTime()),
            () -> assertThrows(ConcurrentRequestException.class,
                () -> capabilityRepository.updateMembership(stale, AgreementCapabilityStatus.DISABLED, null, LATER)),
            () -> assertThrows(ConcurrentRequestException.class, () -> capabilityRepository.updateExecutionStatus(stale,
                AgreementCapabilityExecutionStatus.SUSPENDED_BY_PREREQUISITE, LATER)),
            () -> assertThrows(ConcurrentRequestException.class, () -> capabilityRepository.updateTerms(stale,
                AgreementCapabilityBuilder.EFFECTIVE_FROM, null, null, null, false, LATER)),
            () -> assertEquals(AgreementCapabilityExecutionStatus.ENABLED, stale.getExecutionStatus()));

        testMapper.touchAgreementCapability(capability.getAgreementCapabilityId(), LATER.plusSeconds(5));

        assertAll(() -> assertThrows(ConcurrentRequestException.class, () -> capabilityRepository.updateTerms(capability,
            AgreementCapabilityBuilder.EFFECTIVE_FROM, null, null, null, false, LATER.plusSeconds(6))),
            () -> assertEquals(effectiveTo, capability.getEffectiveTo()),
            () -> assertEquals(effectiveTo,
                capabilityRepository.findById(capability.getAgreementCapabilityId()).getEffectiveTo()));
    }
}
