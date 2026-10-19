package com.paytech.commercial.auto.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.CommercialPriceErrorCode;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.domain.CommercialPriceCandidate;
import com.paytech.commercial.domain.PricingFacts;
import com.paytech.commercial.test.builder.CommercialTermPriceBuilder;
import com.paytech.commercial.test.builder.PricingProfileBuilder;
import com.paytech.commercial.test.builder.PricingProfileCondBuilder;
import com.paytech.foundation.common.error.FailException;

/**
 * 命中算法的公开静态合同（technique.md §2.4）：歧义分支在数据库唯一键（同版本 / OE / 币种的 priorityNo 唯一）下不可经 HTTP 构造，
 * 这里直接以内存候选验证失败关闭，同时固定排序规则
 *
 * @see CommercialPriceCandidate#selectHit(List, PricingFacts)
 */
class CommercialPriceCandidateTest {
    /** 同具体度、同优先级的两条命中：409 PRICE_AMBIGUOUS，不按插入顺序随意取一条。 */
    @Test
    void test_ambiguousHit() {
        CommercialPriceCandidate visa = candidate(1, "P_VISA", 10, PricingDimension.CARD_BRAND, "VISA");
        CommercialPriceCandidate credit = candidate(2, "P_CREDIT", 10, PricingDimension.FUNDING_TYPE, "CREDIT");

        FailException failure = assertThrows(FailException.class,
            () -> CommercialPriceCandidate.selectHit(List.of(visa, credit), facts()));

        assertEquals(CommercialPriceErrorCode.PRICE_AMBIGUOUS.getCode(), failure.getCode());
    }

    /** 具体度优先于优先级；同具体度时优先级小者胜。 */
    @Test
    void test_ordering() {
        CommercialPriceCandidate generic = candidate(1, "P_CARD", 1, null, null);
        CommercialPriceCandidate visa = candidate(2, "P_VISA", 20, PricingDimension.CARD_BRAND, "VISA");
        CommercialPriceCandidate credit = candidate(3, "P_CREDIT", 15, PricingDimension.FUNDING_TYPE, "CREDIT");

        assertAll(() -> assertEquals(credit, CommercialPriceCandidate.selectHit(List.of(generic, visa, credit), facts())),
            () -> assertEquals(generic, CommercialPriceCandidate.selectHit(List.of(generic),
                PricingFacts.parse(CommercialCodes.PaymentMethod.CARD, "USD", Map.of()))));
    }

    /** VISA + CREDIT 事实。 */
    static PricingFacts facts() {
        return PricingFacts.parse(CommercialCodes.PaymentMethod.CARD, "USD",
            Map.of(PricingDimension.CARD_BRAND.getCode(), "VISA", PricingDimension.FUNDING_TYPE.getCode(), "CREDIT"));
    }

    /** 一条 USD 卡价格及其单维度条件（dimension 为空表示无条件）。 */
    static CommercialPriceCandidate candidate(long id, String code, int priority, PricingDimension dimension, String value) {
        var profile = PricingProfileBuilder.template().pricingProfileId(id).pricingProfileCode(code).build();
        var price = CommercialTermPriceBuilder.template().termPriceId(id).pricingProfileCode(code).priorityNo(priority).build();
        var conditions = dimension == null ? List.<com.paytech.commercial.core.dal.dataobject.PricingProfileCond>of()
            : List.of(PricingProfileCondBuilder.template().pricingProfileId(id).dimensionCode(dimension).conditionValue(value)
                .build());
        return new CommercialPriceCandidate(price, profile, conditions);
    }
}
