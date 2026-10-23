package com.paytech.billing.auto.mq;

import static com.paytech.billing.test.builder.PaymentFactConstants.DEFAULT_CALENDAR;
import static com.paytech.billing.test.builder.PaymentFactConstants.END_OF_DAY_CUTOFF;
import static com.paytech.billing.test.builder.PaymentFactConstants.PAYMENT_ATTEMPT_ID;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.billing.core.enums.CalendarDayType;
import com.paytech.billing.core.enums.CalendarStatus;
import com.paytech.billing.ext.mq.OrderPaymentSucceededBillingHandler;
import com.paytech.billing.test.base.AbstractOrderPaymentSucceededTest;
import com.paytech.billing.test.builder.CommercialPriceComponentBuilder;
import com.paytech.billing.test.builder.CommercialPriceResponseBuilder;
import com.paytech.billing.test.builder.OrderPaymentSucceededEnvelopeBuilder;
import com.paytech.foundation.messaging.api.DomainEvent;

/**
 * T+N 到期转可用时间（技术方案 4.7，S8）：截止前后、跨周末、例外日、N = 0、非 UTC 结算时区与非 DEFAULT 日历
 * <li>2026-10-21 为周三；DEFAULT 日历无例外日时周一至周五为工作日。</li>
 *
 * @see OrderPaymentSucceededBillingHandler#consume(DomainEvent)
 */
class OrderPaymentSucceededSettlementTimeTest extends AbstractOrderPaymentSucceededTest {
    /** 结算时区当日 14:00 的截止秒。 */
    private static final int CUTOFF_14_00 = 50_400;

    /** 例外日主键起点。 */
    private static final long CALENDAR_DAY_ID = 59901L;

    /** 测试专用的非 DEFAULT 日历代码。 */
    private static final String TEST_CALENDAR = "TEST_GULF_DAYS";

    /** 阿联酋结算时区。 */
    private static final String DUBAI = "Asia/Dubai";

    /** 本类全部用例共用清算账户与渠道成本。 */
    @BeforeEach
    void setUpClassMocks() {
        mockChannel();
    }

    /** 截止前（13:00 ≤ 14:00）：周三起算，T+1 为周四日终。 */
    @Test
    void test_beforeCutoff() {
        assertAvailableTime("2026-10-21T13:00:00Z", settlement(1, CommercialPriceResponseBuilder.UTC, CUTOFF_14_00),
            "2026-10-22T23:59:59Z");
    }

    /** 截止后（15:00 > 14:00）：周四起算，T+1 为周五日终。 */
    @Test
    void test_afterCutoff() {
        assertAvailableTime("2026-10-21T15:00:00Z", settlement(1, CommercialPriceResponseBuilder.UTC, CUTOFF_14_00),
            "2026-10-23T23:59:59Z");
    }

    /** 周五成功、T+1 跨周末到周一。 */
    @Test
    void test_acrossWeekend() {
        assertAvailableTime("2026-10-23T08:00:00Z", defaultSettlement(1), "2026-10-26T23:59:59Z");
    }

    /** 例外日优先：周一登记为节假日，周五 T+1 顺延到周二。 */
    @Test
    void test_calendarExceptions() {
        testMapper.insertCalendarDay(CALENDAR_DAY_ID, DEFAULT_CALENDAR, LocalDate.parse("2026-10-26"),
            CalendarDayType.NON_WORKING.getCode(), "合成节假日");
        assertAvailableTime("2026-10-23T08:00:00Z", defaultSettlement(1), "2026-10-27T23:59:59Z");
    }

    /** 周六调休为工作日：周五 T+1 落在周六。 */
    @Test
    void test_weekendWorkingDay() {
        testMapper.insertCalendarDay(CALENDAR_DAY_ID, DEFAULT_CALENDAR, LocalDate.parse("2026-10-24"),
            CalendarDayType.WORKING.getCode(), "合成调休");
        assertAvailableTime("2026-10-23T08:00:00Z", defaultSettlement(1), "2026-10-24T23:59:59Z");
    }

    /** N = 0 且起算日为工作日：当天日终。 */
    @Test
    void test_sameDaySettlement() {
        assertAvailableTime("2026-10-21T08:00:00Z", defaultSettlement(0), "2026-10-21T23:59:59Z");
    }

    /** N = 0 且起算日为周六：顺延到周一日终。 */
    @Test
    void test_sameDaySettlementOnWeekend() {
        assertAvailableTime("2026-10-24T08:00:00Z", defaultSettlement(0), "2026-10-26T23:59:59Z");
    }

    /** Asia/Dubai（UTC+4）：周三 21:00Z 为当地周四 01:00，T+1 为周五 23:59:59 当地 = 19:59:59Z。 */
    @Test
    void test_settlementTimezone() {
        assertAvailableTime("2026-10-21T21:00:00Z", settlement(1, DUBAI, END_OF_DAY_CUTOFF),
            "2026-10-23T19:59:59Z");
    }

    /**
     * 非 DEFAULT 日历（Asia/Dubai，形同 AE_BUSINESS_DAYS）：例外日只按价格引用的日历代码生效
     * <li>周三 21:00Z 为当地周四 01:00，T+1 本为周五，但该日历周五为节假日，顺延到周一 23:59:59 当地 = 19:59:59Z。</li>
     * <li>测试专用日历由用例插入、清理时删除；AE_BUSINESS_DAYS 本身是初始化 DML 保留行，本机执行变更 SQL 前可能不存在，故不直接使用。</li>
     */
    @Test
    void test_nonDefaultCalendar() {
        testMapper.insertCalendar(TEST_CALENDAR, "测试海湾工作日", DUBAI, CalendarStatus.ACTIVE.getCode());
        testMapper.insertCalendarDay(CALENDAR_DAY_ID, TEST_CALENDAR, LocalDate.parse("2026-10-23"),
            CalendarDayType.NON_WORKING.getCode(), "合成节假日");

        assertAvailableTime("2026-10-21T21:00:00Z", CommercialPriceResponseBuilder.clientPricing()
            .settlement(1, TEST_CALENDAR, DUBAI, END_OF_DAY_CUTOFF), "2026-10-26T19:59:59Z");
    }

    /** 以给定结算四字段与成功时间登记，断言到期转可用时间。 */
    private void assertAvailableTime(String succeededAt, CommercialPriceResponseBuilder price, String expected) {
        mockClientPrice(price.components(CommercialPriceComponentBuilder.percentage(CLIENT_FEE_COMPONENT_ID, "0.029")
            .build()).build());

        consumed(OrderPaymentSucceededEnvelopeBuilder.template().succeededAt(Instant.parse(succeededAt)).build());

        fact(PAYMENT_ATTEMPT_ID).times(Instant.parse(succeededAt), Instant.parse(expected));
    }

    /** DEFAULT 日历、UTC、截止 86399、T+N。 */
    private static CommercialPriceResponseBuilder defaultSettlement(int businessDays) {
        return settlement(businessDays, CommercialPriceResponseBuilder.UTC, END_OF_DAY_CUTOFF);
    }

    /** DEFAULT 日历与给定 N、时区、截止秒。 */
    private static CommercialPriceResponseBuilder settlement(int businessDays, String timezone, int cutoff) {
        return CommercialPriceResponseBuilder.clientPricing().settlement(businessDays, DEFAULT_CALENDAR, timezone, cutoff);
    }
}
