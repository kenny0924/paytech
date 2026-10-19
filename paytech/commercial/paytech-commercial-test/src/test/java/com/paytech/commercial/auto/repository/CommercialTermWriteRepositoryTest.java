package com.paytech.commercial.auto.repository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.BandBasis;
import com.paytech.commercial.api.enums.CommercialTermPlanStatus;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponentBand;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.enums.CommercialTermComponentBandMode;
import com.paytech.commercial.domain.enums.CommercialTermComponentComponentType;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentBandRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPlanRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPriceRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.commercial.test.base.CommercialMockTestBase;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.builder.CommercialTermComponentBandBuilder;
import com.paytech.commercial.test.builder.CommercialTermComponentBuilder;
import com.paytech.commercial.test.builder.CommercialTermPlanBuilder;
import com.paytech.commercial.test.builder.CommercialTermPriceBuilder;
import com.paytech.commercial.test.builder.CommercialTermVersionBuilder;
import com.paytech.foundation.common.error.ConcurrentRequestException;

/**
 * 商业条款五表的完整行写入映射：方案三个唯一键、版本号递增与生效槽、版本状态与区间的条件更新、价格 / 组件 / 档位的整体替换；真实 MyBatis 与本地
 * mor_commercial。
 *
 * @see CommercialTermPlanRepository#findByAgreementAndType(long, String)
 * @see CommercialTermPlanRepository#updateStatus(CommercialTermPlan, CommercialTermPlanStatus, Instant)
 * @see CommercialTermVersionRepository#findMaxVersionNo(long)
 * @see CommercialTermVersionRepository#updateStatus(CommercialTermVersion, CommercialTermVersionStatus, Integer, Instant)
 * @see CommercialTermVersionRepository#updateEffectivePeriod(CommercialTermVersion, Instant, Instant, Instant)
 * @see CommercialTermPriceRepository#insertAll(List)
 * @see CommercialTermPriceRepository#deleteByVersionId(long)
 * @see CommercialTermComponentRepository#findByPriceIds(java.util.Collection)
 * @see CommercialTermComponentRepository#deleteByPriceIds(java.util.Collection)
 * @see CommercialTermComponentBandRepository#findByComponentIds(java.util.Collection)
 * @see CommercialTermComponentBandRepository#deleteByComponentIds(java.util.Collection)
 */
class CommercialTermWriteRepositoryTest extends CommercialMockTestBase {
    /** 更新时点。 */
    private static final Instant LATER = Instant.parse("2026-09-12T08:30:00.123456Z");

    /** 第 2 版。 */
    private static final long SECOND_VERSION_ID = CommercialTermVersionBuilder.TERM_VERSION_ID + 1;

    /** AED 价格。 */
    private static final long AED_PRICE_ID = CommercialTermPriceBuilder.TERM_PRICE_ID + 1;

    /** 阶梯组件。 */
    private static final long TIERED_COMPONENT_ID = CommercialTermComponentBuilder.TERM_COMPONENT_ID + 1;

    @Autowired
    private CommercialTermPlanRepository planRepository;

    @Autowired
    private CommercialTermPriceRepository priceRepository;

    @Autowired
    private CommercialTermVersionRepository versionRepository;

    @Autowired
    private CommercialTermComponentRepository componentRepository;

    @Autowired
    private CommercialTermComponentBandRepository bandRepository;

    /** 方案按三个唯一键读取并激活；版本号可递增，同方案只能一个版本占生效槽，区间以状态与 update_time 为条件改写。 */
    @Test
    void test_planAndVersionLifecycle() {
        CommercialTermPlan plan = CommercialTermPlanBuilder.template().build();
        planRepository.insert(plan);
        CommercialTermVersion first = CommercialTermVersionBuilder.template().build();
        CommercialTermVersion second = CommercialTermVersionBuilder.template().termVersionId(SECOND_VERSION_ID).versionNo(2L).build();
        versionRepository.insert(first);
        versionRepository.insert(second);
        CommercialTermVersion stale = versionRepository.findById(SECOND_VERSION_ID);

        planRepository.updateStatus(plan, CommercialTermPlanStatus.ACTIVE, LATER);
        versionRepository.updateStatus(first, CommercialTermVersionStatus.ACTIVE, 1, LATER);
        versionRepository.updateEffectivePeriod(second, Instant.parse("2026-09-20T00:00:00Z"), null, LATER);

        assertAll(() -> assertEquals(CommercialTermPlanStatus.ACTIVE,
            planRepository.findByTypeAndCode(CommercialCodes.TermType.CLIENT_PRICING, CommercialTermPlanBuilder.PLAN_CODE).getStatus()),
            () -> assertEquals(plan.getTermPlanId(), planRepository
                .findByAgreementAndType(AgreementBuilder.AGREEMENT_ID, CommercialCodes.TermType.CLIENT_PRICING).getTermPlanId()),
            () -> assertNull(planRepository.findByPartnerAndType(AgreementBuilder.CLIENT_ID, CommercialCodes.TermType.PARTNER_SHARE)),
            () -> assertEquals(1, planRepository.findByAgreementId(AgreementBuilder.AGREEMENT_ID).size()),
            () -> assertEquals(2L, versionRepository.findMaxVersionNo(CommercialTermPlanBuilder.TERM_PLAN_ID)),
            () -> assertNull(versionRepository.findMaxVersionNo(1L)),
            () -> assertEquals(List.of(1L, 2L), versionRepository.findByPlanId(CommercialTermPlanBuilder.TERM_PLAN_ID).stream()
                .map(CommercialTermVersion::getVersionNo).toList()),
            () -> assertEquals(1, versionRepository.findByPlanAndVersionNo(CommercialTermPlanBuilder.TERM_PLAN_ID, 1L).getActiveSlot()),
            () -> assertEquals(Instant.parse("2026-09-20T00:00:00Z"), versionRepository.findById(SECOND_VERSION_ID).getEffectiveFrom()),
            () -> assertThrows(DuplicateKeyException.class,
                () -> versionRepository.updateStatus(second, CommercialTermVersionStatus.ACTIVE, 1, LATER)),
            () -> assertThrows(ConcurrentRequestException.class, () -> versionRepository.updateEffectivePeriod(stale,
                Instant.parse("2026-09-21T00:00:00Z"), null, LATER)),
            () -> assertThrows(DuplicateKeyException.class, () -> planRepository.insert(CommercialTermPlanBuilder.template()
                .termPlanId(CommercialTermPlanBuilder.TERM_PLAN_ID + 1).planCode("WRITE-PLAN-2").build())));
    }

    /** 价格、组件与档位按父读取；删除自下而上整体替换并返回行数；空集合不访问数据库。 */
    @Test
    void test_priceComponentBandReplaceAsAWhole() {
        priceRepository.insertAll(List.of(CommercialTermPriceBuilder.template().build(),
            CommercialTermPriceBuilder.template().termPriceId(AED_PRICE_ID).currency("AED").build()));
        componentRepository.insertAll(List.of(CommercialTermComponentBuilder.template().build(),
            CommercialTermComponentBuilder.template().termComponentId(TIERED_COMPONENT_ID).termPriceId(AED_PRICE_ID)
                .componentType(CommercialTermComponentComponentType.TIERED).rate(null).currency("AED")
                .bandBasis(BandBasis.TRANSACTION_AMOUNT).bandMode(CommercialTermComponentBandMode.WHOLE_BASE).build()));
        bandRepository.insertAll(List.of(CommercialTermComponentBandBuilder.template().termComponentId(TIERED_COMPONENT_ID).build(),
            CommercialTermComponentBandBuilder.template().termBandId(CommercialTermComponentBandBuilder.TERM_BAND_ID + 1)
                .termComponentId(TIERED_COMPONENT_ID).bandNo(2).lowerBound(new BigDecimal("1000")).upperBound(null).build()));

        CommercialTermPrice aed = priceRepository.findByUniqueKey(CommercialTermVersionBuilder.TERM_VERSION_ID,
            AgreementBuilder.ENTITY_NO, "AED", CommercialTermPriceBuilder.template().build().getPricingProfileCode());
        List<CommercialTermComponent> components = componentRepository.findByPriceIds(
            List.of(CommercialTermPriceBuilder.TERM_PRICE_ID, AED_PRICE_ID));
        List<CommercialTermComponentBand> bands = bandRepository.findByComponentIds(List.of(TIERED_COMPONENT_ID));
        assertAll(() -> assertEquals(AED_PRICE_ID, aed.getTermPriceId()),
            () -> assertEquals(CommercialCodes.AE_BUSINESS_DAYS, aed.getBusinessCalendarCode()),
            () -> assertEquals(2, priceRepository.findByVersionId(CommercialTermVersionBuilder.TERM_VERSION_ID).size()),
            () -> assertEquals(2, components.size()), () -> assertEquals(BandBasis.TRANSACTION_AMOUNT, components.get(1).getBandBasis()),
            () -> assertEquals(0, new BigDecimal("0.025").compareTo(components.get(0).getRate())),
            () -> assertEquals(TIERED_COMPONENT_ID,
                componentRepository.findByUniqueKey(AED_PRICE_ID, "CLIENT_FEE").getTermComponentId()),
            () -> assertEquals(List.of(1, 2), bands.stream().map(CommercialTermComponentBand::getBandNo).toList()),
            () -> assertNull(bands.get(1).getUpperBound()),
            () -> assertThrows(DuplicateKeyException.class, () -> priceRepository.insertAll(List.of(CommercialTermPriceBuilder.template()
                .termPriceId(AED_PRICE_ID + 1).priorityNo(2).build()))));

        int bandsDeleted = bandRepository.deleteByComponentIds(List.of(CommercialTermComponentBuilder.TERM_COMPONENT_ID, TIERED_COMPONENT_ID));
        int componentsDeleted = componentRepository.deleteByPriceIds(List.of(CommercialTermPriceBuilder.TERM_PRICE_ID, AED_PRICE_ID));
        int pricesDeleted = priceRepository.deleteByVersionId(CommercialTermVersionBuilder.TERM_VERSION_ID);

        assertAll(() -> assertEquals(2, bandsDeleted), () -> assertEquals(2, componentsDeleted), () -> assertEquals(2, pricesDeleted),
            () -> assertEquals(0, componentRepository.deleteByPriceIds(List.of())),
            () -> assertEquals(List.of(), componentRepository.findByPriceIds(List.of())));
    }
}
