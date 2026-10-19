package com.paytech.commercial.auto.repository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.CalculationBase;
import com.paytech.commercial.api.enums.CheckoutErrorCode;
import com.paytech.commercial.api.enums.CommercialTermPlanStatus;
import com.paytech.commercial.api.enums.FeeTaxTreatment;
import com.paytech.commercial.api.enums.FundMode;
import com.paytech.commercial.api.enums.PricingAuthority;
import com.paytech.commercial.api.enums.PricingModel;
import com.paytech.commercial.api.enums.PricingProfileKind;
import com.paytech.commercial.api.enums.RefundBehavior;
import com.paytech.commercial.domain.CommercialCheckoutFacts;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domainservice.service.CheckoutQueryService;
import com.paytech.commercial.test.base.CommercialMockTestBase;
import com.paytech.commercial.test.dal.CommercialTestMapper;
import com.paytech.foundation.common.error.ErrorException;

/**
 * Checkout 只读快照对第三轮新列与 S5 闭合枚举的映射：真实 MyBatis 与本地 mor_commercial，夹具 checkout-query.json。
 * <li>数据库中出现闭合枚举之外的 Code 时当前读取失败关闭（503 OWNER_UNAVAILABLE 语义），不映射为 null。</li>
 *
 * @see CheckoutQueryService#load(CommercialCheckoutScope)
 */
class CheckoutFactsMappingTest extends CommercialMockTestBase {
    /** 夹具第一份合同下的组件。 */
    private static final long TERM_COMPONENT_ID = 910000000000115L;

    @Autowired
    private CommercialTestMapper testMapper;

    /** 合同商业关系、版本定价模型与税费口径、价格币种、组件计算基数与退款口径、方案状态与画像种类均读回。 */
    @Test
    void test_newColumnsAreMapped() {
        fixture.install();

        CommercialCheckoutFacts facts = fixture.readFacts();

        var agreement = facts.agreements().get(0);
        var plan = facts.termPlans().get(0);
        var version = facts.termVersions().get(0);
        var price = facts.termPrices().get(0);
        var component = facts.termComponents().get(0);
        var profile = facts.methodProfiles().get(0);
        assertAll(() -> assertEquals(BusinessMode.MOR, agreement.businessMode()),
            () -> assertEquals(FundMode.SETTLE_THROUGH_PLATFORM, agreement.fundMode()),
            () -> assertEquals(PricingAuthority.PLATFORM, agreement.pricingAuthority()),
            () -> assertNull(agreement.referralPartnerId()),
            () -> assertEquals(CommercialCodes.TermType.CLIENT_PRICING, plan.termType()),
            () -> assertEquals(CommercialTermPlanStatus.ACTIVE, plan.status()),
            () -> assertEquals(PricingModel.BLENDED, version.pricingModel()),
            () -> assertEquals(FeeTaxTreatment.EXCLUSIVE, version.feeTaxTreatment()),
            () -> assertEquals("USD", price.currency()),
            () -> assertEquals(CommercialCodes.AE_BUSINESS_DAYS, price.businessCalendarCode()),
            () -> assertEquals(CommercialCodes.FeeEvent.SALE, component.triggerCode()),
            () -> assertEquals(CalculationBase.GROSS_AMOUNT, component.calculationBase()),
            () -> assertEquals(RefundBehavior.RETURN_PRORATA, component.refundBehavior()),
            () -> assertNull(component.bandBasis()), () -> assertNull(component.bandMode()),
            () -> assertEquals(PricingProfileKind.PAYMENT_METHOD, profile.profileKind()));
    }

    /** 计算基数为闭合枚举之外的旧值时整次读取失败关闭，不静默丢弃组件。 */
    @Test
    void test_unknownCalculationBaseFailsClosed() {
        fixture.install();
        testMapper.overwriteComponentCalculationBase(TERM_COMPONENT_ID, "PAYMENT_AMOUNT");

        ErrorException failure = assertThrows(ErrorException.class, fixture::readFacts);

        assertEquals(CheckoutErrorCode.OWNER_UNAVAILABLE.getCode(), failure.getErrorCode().getCode());
    }
}
