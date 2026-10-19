package com.paytech.commercial.auto.repository;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import com.paytech.commercial.api.constant.CommercialCodes.CardBrand;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.PricingProfileKind;
import com.paytech.commercial.core.dal.dataobject.PricingProfile;
import com.paytech.commercial.core.dal.dataobject.PricingProfileCond;
import com.paytech.commercial.domain.enums.PricingProfileStatus;
import com.paytech.commercial.domainservice.repository.PricingProfileCondRepository;
import com.paytech.commercial.domainservice.repository.PricingProfileRepository;
import com.paytech.commercial.test.base.CommercialMockTestBase;
import com.paytech.commercial.test.builder.PricingProfileBuilder;
import com.paytech.commercial.test.builder.PricingProfileCondBuilder;
import com.paytech.foundation.common.error.ConcurrentRequestException;

/**
 * 定价画像与条件的完整行写入映射：插入、按代码 / 种类 / 状态读取、DRAFT 内容与状态的条件更新、条件整体替换；真实 MyBatis 与本地 mor_commercial。
 *
 * @see PricingProfileRepository#insert(PricingProfile)
 * @see PricingProfileRepository#findByKindAndStatus(PricingProfileKind, PricingProfileStatus)
 * @see PricingProfileRepository#updateDraft(PricingProfile, String, PricingProfileKind, String, Instant)
 * @see PricingProfileRepository#updateStatus(PricingProfile, PricingProfileStatus, Instant)
 * @see PricingProfileCondRepository#insertAll(List)
 * @see PricingProfileCondRepository#findByProfileIds(java.util.Collection)
 * @see PricingProfileCondRepository#deleteByProfileId(long)
 */
class PricingProfileWriteRepositoryTest extends CommercialMockTestBase {
    /** 更新时点。 */
    private static final Instant LATER = Instant.parse("2026-09-12T08:30:00.123456Z");

    /** 出款通道画像。 */
    private static final long PAYOUT_PROFILE_ID = PricingProfileBuilder.PRICING_PROFILE_ID + 1;

    @Autowired
    private PricingProfileRepository profileRepository;

    @Autowired
    private PricingProfileCondRepository conditionRepository;

    /** 画像按代码与种类 / 状态筛选读取；DRAFT 内容以状态与 update_time 为条件改写，之后激活；陈旧对象失败。 */
    @Test
    void test_profileDraftEditAndActivation() {
        PricingProfile card = PricingProfileBuilder.template().build();
        profileRepository.insert(card);
        profileRepository.insert(PricingProfileBuilder.template().pricingProfileId(PAYOUT_PROFILE_ID).pricingProfileCode("WRITE_PAYOUT")
            .profileKind(PricingProfileKind.PAYOUT_RAIL).paymentMethodCode(null).status(PricingProfileStatus.ACTIVE).build());
        PricingProfile stale = profileRepository.findById(PricingProfileBuilder.PRICING_PROFILE_ID);

        profileRepository.updateDraft(card, "Renamed Card", PricingProfileKind.PAYMENT_METHOD, card.getPaymentMethodCode(), LATER);
        profileRepository.updateStatus(card, PricingProfileStatus.ACTIVE, LATER.plusSeconds(1));

        PricingProfile read = profileRepository.findByCode(PricingProfileBuilder.PRICING_PROFILE_CODE);
        assertAll(() -> assertEquals("Renamed Card", read.getProfileName()),
            () -> assertEquals(PricingProfileStatus.ACTIVE, read.getStatus()),
            () -> assertNull(profileRepository.findById(PAYOUT_PROFILE_ID).getPaymentMethodCode()),
            () -> assertEquals(2, profileRepository.findByCodes(List.of("WRITE_PAYOUT", PricingProfileBuilder.PRICING_PROFILE_CODE)).size()),
            () -> assertEquals(2, profileRepository.findByKindAndStatus(null, null).size()),
            () -> assertEquals(1, profileRepository.findByKindAndStatus(PricingProfileKind.PAYOUT_RAIL, null).size()),
            () -> assertEquals(List.of(), profileRepository.findByKindAndStatus(PricingProfileKind.FX_PAIR, PricingProfileStatus.ACTIVE)),
            () -> assertThrows(ConcurrentRequestException.class,
                () -> profileRepository.updateDraft(stale, "Stale", PricingProfileKind.PAYMENT_METHOD, null, LATER)),
            () -> assertThrows(ConcurrentRequestException.class,
                () -> profileRepository.updateStatus(stale, PricingProfileStatus.RETIRED, LATER)),
            () -> assertThrows(DuplicateKeyException.class, () -> profileRepository.insert(
                PricingProfileBuilder.template().pricingProfileId(PAYOUT_PROFILE_ID + 1).build())));
    }

    /** 条件按画像读取并按主键排序；同画像同维度同值唯一；整体删除返回行数。 */
    @Test
    void test_conditionsReplaceAsAWhole() {
        conditionRepository.insertAll(List.of(PricingProfileCondBuilder.template().build(), PricingProfileCondBuilder.template()
            .profileConditionId(PricingProfileCondBuilder.PROFILE_CONDITION_ID + 1).conditionValue(CardBrand.MASTERCARD).build()));

        List<PricingProfileCond> conditions = conditionRepository.findByProfileId(PricingProfileBuilder.PRICING_PROFILE_ID);
        assertAll(() -> assertEquals(List.of(CardBrand.VISA, CardBrand.MASTERCARD),
            conditions.stream().map(PricingProfileCond::getConditionValue).toList()),
            () -> assertEquals(PricingDimension.CARD_BRAND, conditions.get(0).getDimensionCode()),
            () -> assertEquals(2, conditionRepository.findByProfileIds(List.of(PricingProfileBuilder.PRICING_PROFILE_ID)).size()),
            () -> assertEquals(List.of(), conditionRepository.findByProfileIds(List.of())),
            () -> assertThrows(DuplicateKeyException.class, () -> conditionRepository.insertAll(List.of(PricingProfileCondBuilder
                .template().profileConditionId(PricingProfileCondBuilder.PROFILE_CONDITION_ID + 2).build()))));

        int deleted = conditionRepository.deleteByProfileId(PricingProfileBuilder.PRICING_PROFILE_ID);

        assertAll(() -> assertEquals(2, deleted),
            () -> assertEquals(List.of(), conditionRepository.findByProfileId(PricingProfileBuilder.PRICING_PROFILE_ID)));
    }
}
