package com.paytech.billing.auto.repository;

import static com.paytech.billing.test.builder.BillingFactRequestBuilder.CHANNEL_ID;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.CLIENT_ID;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.CURRENCY;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.ENTITY_NO;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactOriginType;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.enums.LineLinkType;
import com.paytech.billing.core.dal.dataobject.BillingFact;
import com.paytech.billing.core.dal.dataobject.BillingFactLineLink;
import com.paytech.billing.core.dal.dataobject.BillingFactPricing;
import com.paytech.billing.core.dal.dataobject.BusinessCalendar;
import com.paytech.billing.core.dal.dataobject.BusinessCalendarDay;
import com.paytech.billing.core.enums.CalendarDayType;
import com.paytech.billing.core.enums.CalendarStatus;
import com.paytech.billing.domainservice.repository.BillingFactLineLinkRepository;
import com.paytech.billing.domainservice.repository.BillingFactPricingRepository;
import com.paytech.billing.domainservice.repository.BillingFactRepository;
import com.paytech.billing.domainservice.repository.BusinessCalendarDayRepository;
import com.paytech.billing.domainservice.repository.BusinessCalendarRepository;
import com.paytech.billing.test.base.AbstractBillingFactApiTest;
import com.paytech.billing.test.builder.BillingFactRequestBuilder;
import com.paytech.billing.test.dal.BillingTestMapper;

import tools.jackson.databind.JsonNode;

/**
 * 本期没有生产写入方或调用方的持久化路径（自动事实与 T+N 计算在后续切片）：真实 MyBatis 与本地 mor_billing 上验证映射、索引访问与约束
 * <li>定价输入的插入与读取（含 1:1 主键约束）以及按 ID 接口返回定价；行链接按原行 / 新行读取；按替代链与派生来源读取事实；日历只读副本。</li>
 *
 * @see BillingFactPricingRepository#insert(BillingFactPricing)
 * @see BillingFactPricingRepository#findByFactId(long)
 * @see BillingFactLineLinkRepository#findBySourceLineIds(java.util.Collection)
 * @see BillingFactRepository#findBySupersedesFactId(long)
 * @see BillingFactRepository#findByParentFactId(long)
 * @see BusinessCalendarRepository#findByCode(String)
 * @see BusinessCalendarDayRepository#findBetween(String, LocalDate, LocalDate)
 */
class BillingRepositoryTest extends AbstractBillingFactApiTest {
    /** 前置 PAYMENT 事实。 */
    private static final long PAYMENT_FACT_ID = 2_200_001L;

    @Autowired
    private BillingFactRepository billingFactRepository;

    @Autowired
    private BillingFactPricingRepository pricingRepository;

    @Autowired
    private BillingFactLineLinkRepository lineLinkRepository;

    @Autowired
    private BusinessCalendarRepository calendarRepository;

    @Autowired
    private BusinessCalendarDayRepository calendarDayRepository;

    /** 定价输入插入后按 ID、按一组 ID 与按 ID 接口读取一致；同一事实第二行违反主键。 */
    @Test
    void test_pricingInsertAndRead() {
        insertPayment(PAYMENT_FACT_ID);
        BillingFactPricing pricing = pricing(PAYMENT_FACT_ID);

        pricingRepository.insert(pricing);

        BillingFactPricing read = pricingRepository.findByFactId(PAYMENT_FACT_ID);
        JsonNode body = getFact(PAYMENT_FACT_ID).assertSuccess(200).json().path("pricing");
        assertAll(() -> assertEquals(pricing.getCommercialPriceId(), read.getCommercialPriceId()),
            () -> assertEquals(pricing.getCommercialContextTime(), read.getCommercialContextTime()),
            () -> assertEquals("0005", read.getMccCode()), () -> assertNull(read.getPartnerAgreementOeId()),
            () -> assertEquals(1, pricingRepository.findByFactIds(List.of(PAYMENT_FACT_ID, 1L)).size()),
            () -> assertEquals("9001", body.path("commercialPriceId").asString()),
            () -> assertEquals("2", body.path("settlementBusinessDays").asString()),
            () -> assertEquals(BillingTestMapper.RESERVED_CALENDAR, body.path("businessCalendarCode").asString()),
            () -> assertEquals("86399", body.path("cutoffSecondOfDay").asString()),
            () -> assertNull(pricingRepository.findByFactId(1L)));
        assertThrows(DuplicateKeyException.class, () -> pricingRepository.insert(pricing(PAYMENT_FACT_ID)));
    }

    /** 行链接按原行与新行两个方向读取；空集合不访问数据库。 */
    @Test
    void test_lineLinksBySourceAndTarget() {
        insertPayment(PAYMENT_FACT_ID);
        testMapper.insertRuleLine(2_200_011L, PAYMENT_FACT_ID, 1, FactComponentType.CHANNEL_COST.getCode(),
            FactPartyRole.OPERATING_ENTITY.getCode(), ENTITY_NO, FactPartyRole.CHANNEL.getCode(), CHANNEL_ID,
            new BigDecimal("5"), null, new BigDecimal("5"), new BigDecimal("5"), FactLineDirection.CHARGE.getCode(), null,
            null, null, null);
        testMapper.insertRuleLine(2_200_012L, PAYMENT_FACT_ID, 2, FactComponentType.CHANNEL_COST.getCode(),
            FactPartyRole.CHANNEL.getCode(), CHANNEL_ID, FactPartyRole.OPERATING_ENTITY.getCode(), ENTITY_NO,
            new BigDecimal("1"), null, new BigDecimal("1"), new BigDecimal("1"), FactLineDirection.ADJUSTMENT.getCode(),
            null, null, null, null);
        testMapper.insertLineLink(2_200_021L, 2_200_011L, 2_200_012L, LineLinkType.ADJUSTMENT.getCode(),
            new BigDecimal("1"));

        List<BillingFactLineLink> bySource = lineLinkRepository.findBySourceLineIds(List.of(2_200_011L));
        List<BillingFactLineLink> byTarget = lineLinkRepository.findByTargetLineIds(List.of(2_200_012L));
        assertAll(() -> assertEquals(1, bySource.size()), () -> assertEquals(2_200_012L, bySource.get(0).getTargetLineId()),
            () -> assertEquals(LineLinkType.ADJUSTMENT, bySource.get(0).getLinkType()),
            () -> assertEquals(0, new BigDecimal("1").compareTo(bySource.get(0).getLinkedAmount())),
            () -> assertEquals(1, byTarget.size()), () -> assertEquals(2_200_011L, byTarget.get(0).getSourceLineId()),
            () -> assertEquals(List.of(), lineLinkRepository.findBySourceLineIds(List.of(2_200_012L))),
            () -> assertEquals(List.of(), lineLinkRepository.findByTargetLineIds(List.of())));
    }

    /** 按替代链与派生来源读取事实。 */
    @Test
    void test_findBySupersedesAndParent() {
        insertPayment(PAYMENT_FACT_ID);
        insertPayment(PAYMENT_FACT_ID + 1);
        insertPayment(PAYMENT_FACT_ID + 2);
        testMapper.updateParentFact(PAYMENT_FACT_ID + 1, PAYMENT_FACT_ID);
        long correction = submitDraft(BillingFactRequestBuilder.template());
        long corrected = submitApprovedAndPublished(BillingFactRequestBuilder.template());
        long replacement = submitDraft(BillingFactRequestBuilder.template().supersedes(corrected));

        List<BillingFact> children = billingFactRepository.findByParentFactId(PAYMENT_FACT_ID);
        List<BillingFact> replacements = billingFactRepository.findBySupersedesFactId(corrected);
        assertAll(() -> assertEquals(List.of(PAYMENT_FACT_ID + 1), children.stream().map(BillingFact::getId).toList()),
            () -> assertEquals(List.of(replacement), replacements.stream().map(BillingFact::getId).toList()),
            () -> assertEquals(List.of(), billingFactRepository.findBySupersedesFactId(correction)),
            () -> assertEquals(List.of(), billingFactRepository.findByParentFactId(PAYMENT_FACT_ID + 2)));
    }

    /** 日历只读副本：初始化 DML 的 DEFAULT 日历可读，例外日按闭区间与日期升序返回，未知日历为空。 */
    @Test
    void test_calendarReadOnlyCopy() {
        String calendar = BillingTestMapper.RESERVED_CALENDAR;
        testMapper.insertCalendarDay(2_200_101L, calendar, LocalDate.of(2026, 12, 25), CalendarDayType.NON_WORKING.getCode(),
            "Christmas");
        testMapper.insertCalendarDay(2_200_102L, calendar, LocalDate.of(2026, 12, 26), CalendarDayType.WORKING.getCode(),
            null);
        testMapper.insertCalendarDay(2_200_103L, calendar, LocalDate.of(2027, 1, 1), CalendarDayType.NON_WORKING.getCode(),
            null);

        BusinessCalendar read = calendarRepository.findByCode(calendar);
        List<BusinessCalendarDay> days =
            calendarDayRepository.findBetween(calendar, LocalDate.of(2026, 12, 25), LocalDate.of(2026, 12, 31));
        assertAll(() -> assertEquals(CalendarStatus.ACTIVE, read.getStatus()), () -> assertEquals("UTC", read.getTimezone()),
            () -> assertEquals(2, days.size()), () -> assertEquals(CalendarDayType.NON_WORKING, days.get(0).getDayType()),
            () -> assertEquals(LocalDate.of(2026, 12, 26), days.get(1).getCalendarDate()),
            () -> assertNull(days.get(1).getNote()), () -> assertNull(calendarRepository.findByCode("UNKNOWN")));
    }

    /** 插入一条已批准的 AUTO PAYMENT 前置事实。 */
    void insertPayment(long factId) {
        testMapper.insertBillingFact(factId, BillingFactType.PAYMENT.getCode(), FactOriginType.AUTO.getCode(),
            "PAY-" + factId, Long.valueOf(CLIENT_ID), ENTITY_NO, null, null, CURRENCY,
            BillingFactStatus.APPROVED.getCode(), 1, Instant.now());
    }

    /** 一份完整的定价输入（无合作方、非卡支付）。 */
    static BillingFactPricing pricing(long factId) {
        BillingFactPricing pricing = new BillingFactPricing();
        pricing.setFactId(factId);
        pricing.setClientAgreementId(9101L);
        pricing.setCommercialContextTime(Instant.parse("2026-10-19T08:00:00.123456Z"));
        pricing.setConsumerCountryCode("AE");
        pricing.setPaymentMethod("CARD");
        pricing.setIndustryCode("DIGITAL_GOODS");
        pricing.setMccCode("0005");
        pricing.setCommercialPriceId(9001L);
        pricing.setSettlementBusinessDays(2);
        pricing.setBusinessCalendarCode(BillingTestMapper.RESERVED_CALENDAR);
        pricing.setSettlementTimezone("UTC");
        pricing.setCutoffSecondOfDay(86399);
        pricing.setCreateTime(Instant.now().truncatedTo(ChronoUnit.MICROS));
        return pricing;
    }
}
