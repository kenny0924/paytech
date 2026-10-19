package com.paytech.commercial.auto.repository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.commercial.api.enums.BandBasis;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponentBand;
import com.paytech.commercial.domain.enums.CommercialTermComponentBandMode;
import com.paytech.commercial.domain.enums.CommercialTermComponentComponentType;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentBandRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermComponentRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPriceRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.commercial.test.base.CommercialMockTestBase;
import com.paytech.commercial.test.builder.AgreementBuilder;
import com.paytech.commercial.test.builder.CommercialTermComponentBandBuilder;
import com.paytech.commercial.test.builder.CommercialTermComponentBuilder;
import com.paytech.commercial.test.builder.CommercialTermPlanBuilder;
import com.paytech.commercial.test.builder.CommercialTermPriceBuilder;
import com.paytech.commercial.test.builder.CommercialTermVersionBuilder;
import com.paytech.commercial.test.builder.PricingProfileBuilder;
import com.paytech.commercial.test.mocker.local.agreement.CommercialGraphMocker;

/**
 * 合同 → 方案 → 版本 → 价格 → 组件 → 档位数据图经正式 Repository 插入后，自上而下的父子关联可按父读取。
 *
 * @see CommercialTermVersionRepository#findByPlanId(long)
 * @see CommercialTermPriceRepository#findByVersionId(long)
 * @see CommercialTermComponentRepository#findByPriceIds(java.util.Collection)
 * @see CommercialTermComponentBandRepository#findByComponentIds(java.util.Collection)
 */
class CommercialGraphMockerTest extends CommercialMockTestBase {
    @Autowired
    private CommercialGraphMocker graphMocker;

    @Autowired
    private AgreementRepository agreementRepository;

    @Autowired
    private CommercialTermVersionRepository versionRepository;

    @Autowired
    private CommercialTermPriceRepository priceRepository;

    @Autowired
    private CommercialTermComponentRepository componentRepository;

    @Autowired
    private CommercialTermComponentBandRepository bandRepository;

    /** 默认图与追加阶梯组件、两档档位后，各层按父读取的数量和关联一致。 */
    @Test
    void test_graphIsLinkedTopDown() {
        var graph = graphMocker.mockDefaultGraph()
            .component(CommercialTermComponentBuilder.template()
                .termComponentId(CommercialTermComponentBuilder.TERM_COMPONENT_ID + 1).componentNo(2).componentCode("TIERED_FEE")
                .componentType(CommercialTermComponentComponentType.TIERED).rate(null).currency("USD")
                .bandBasis(BandBasis.TRANSACTION_AMOUNT).bandMode(CommercialTermComponentBandMode.WHOLE_BASE))
            .band(CommercialTermComponentBandBuilder.template())
            .band(CommercialTermComponentBandBuilder.template().termBandId(CommercialTermComponentBandBuilder.TERM_BAND_ID + 1)
                .bandNo(2).lowerBound(new BigDecimal("1000")).upperBound(null));
        long tieredId = CommercialTermComponentBuilder.TERM_COMPONENT_ID + 1;

        List<CommercialTermComponentBand> bands = bandRepository.findByComponentIds(List.of(tieredId));
        assertAll(() -> assertEquals(AgreementBuilder.AGREEMENT_NO, agreementRepository.findById(AgreementBuilder.AGREEMENT_ID).getAgreementNo()),
            () -> assertEquals(PricingProfileBuilder.PRICING_PROFILE_CODE, graph.profiles().get(0).getPricingProfileCode()),
            () -> assertEquals(List.of(CommercialTermVersionBuilder.TERM_VERSION_ID),
                versionRepository.findByPlanId(CommercialTermPlanBuilder.TERM_PLAN_ID).stream().map(v -> v.getTermVersionId()).toList()),
            () -> assertEquals(1, priceRepository.findByVersionId(CommercialTermVersionBuilder.TERM_VERSION_ID).size()),
            () -> assertEquals(2, componentRepository.findByPriceIds(List.of(CommercialTermPriceBuilder.TERM_PRICE_ID)).size()),
            () -> assertEquals(2, bands.size()), () -> assertEquals(2, graph.bands().size()),
            () -> assertEquals(tieredId, bands.get(1).getTermComponentId()));
    }
}
