package com.paytech.ledger.auto.ext.http.setup;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.ledger.api.LedgerBookCommandApi;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.api.request.CreateLedgerBookRequestBody;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.core.dal.mapper.LedgerBookMapper;
import com.paytech.ledger.core.enums.BookStatus;
import com.paytech.ledger.core.enums.FinanceEntityStatus;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.core.enums.ReferenceDataStatus;
import com.paytech.ledger.test.base.MockTestBase;
import com.paytech.ledger.test.builder.AccountingSetupRequestBuilder;
import com.paytech.ledger.test.dal.AccountingTestMapper;
import com.paytech.ledger.test.mocker.local.LedgerMocker;
import com.paytech.platform.support.referencedata.client.test.ReferenceDataBuilders;

/**
 * 创建账簿：OE/FE 主体、序号初始化、唯一性和前置资料校验
 * <li>币种只接受 Platform 币种规则快照中 ACTIVE 的币种（AC-03）；INACTIVE、DRAFT 与快照中没有的币种均为 CURRENCY_NOT_ACTIVE。</li>
 *
 * @see LedgerBookCommandApi#createLedgerBook(String, String, CreateLedgerBookRequestBody)
 */
class CreateLedgerBookTest extends MockTestBase {
    private static final String PATH = "/ledger-books/v1";

    @Autowired
    private LedgerBookMapper ledgerBookMapper;

    @Autowired
    private AccountingTestMapper testMapper;

    @Test
    void test_operatingEntityBookWithSequence() {
        var response = postAsOperator(PATH, AccountingSetupRequestBuilder.ledgerBook(LedgerEntityType.OPERATING_ENTITY.getCode(),
            "AE001")).assertSuccess(201);

        long bookId = response.id("ledgerBookId");
        LedgerBook book = ledgerBookMapper.selectById(bookId);
        assertAll(() -> assertEquals(LedgerEntityType.OPERATING_ENTITY, book.getEntityType()),
            () -> assertEquals("AE001", book.getEntityId()), () -> assertEquals("USD", book.getCurrency()),
            () -> assertEquals(LedgerMocker.STANDARD_CHART_ID, book.getChartId()),
            () -> assertEquals(BookStatus.OPEN, book.getBookStatus()),
            () -> assertEquals(1L, testMapper.selectNextSequence(bookId)));
    }

    @Test
    void test_financeEntityBookRequiresActiveEntity() {
        long active = ledgerMocker.mockFinanceEntity("FE-ACTIVE", 880001L, FinanceEntityStatus.ACTIVE);
        long closed = ledgerMocker.mockFinanceEntity("FE-CLOSED", 880002L, FinanceEntityStatus.CLOSED);
        String type = LedgerEntityType.FINANCE_ENTITY.getCode();

        postAsOperator(PATH, AccountingSetupRequestBuilder.ledgerBook(type, String.valueOf(active))).assertSuccess(201);
        postAsOperator(PATH, AccountingSetupRequestBuilder.ledgerBook(type, String.valueOf(closed)))
            .assertRejected(LedgerSetupErrorCode.FINANCE_ENTITY_UNAVAILABLE);
        postAsOperator(PATH, AccountingSetupRequestBuilder.ledgerBook(type, "123456789"))
            .assertRejected(LedgerSetupErrorCode.FINANCE_ENTITY_UNAVAILABLE);
    }

    @Test
    void test_duplicateBookRejected() {
        ledgerMocker.mockBook(LedgerEntityType.OPERATING_ENTITY, "AE001", "USD", LedgerMocker.STANDARD_CHART_ID);

        postAsOperator(PATH, AccountingSetupRequestBuilder.ledgerBook(LedgerEntityType.OPERATING_ENTITY.getCode(), "AE001"))
            .assertRejected(LedgerSetupErrorCode.LEDGER_BOOK_ALREADY_EXISTS);
    }

    /** 快照中没有的币种按 CURRENCY_NOT_ACTIVE 拒绝；请求处理期间不回源 Platform。 */
    @Test
    void test_referenceDataMustBeActive() {
        var unknownCurrency = AccountingSetupRequestBuilder.ledgerBook(LedgerEntityType.OPERATING_ENTITY.getCode(),
            "AE001");
        unknownCurrency.setCurrency("EUR");
        int platformCalls = referenceDataMocker.totalCalls();
        postAsOperator(PATH, unknownCurrency).assertRejected(LedgerSetupErrorCode.CURRENCY_NOT_ACTIVE);
        assertEquals(platformCalls, referenceDataMocker.totalCalls(), "开账簿不应回源 Platform");

        var unknownChart = AccountingSetupRequestBuilder.ledgerBook(LedgerEntityType.OPERATING_ENTITY.getCode(),
            "AE001");
        unknownChart.setChartCode("NOT_EXISTS");
        postAsOperator(PATH, unknownChart).assertRejected(LedgerSetupErrorCode.CHART_NOT_ACTIVE);
    }

    /** Platform 中 INACTIVE（只拦新用途）与 DRAFT（从未生效）的币种都不能开新账簿。 */
    @ParameterizedTest
    @EnumSource(value = com.paytech.platform.support.api.enums.ReferenceDataStatus.class, names = {"INACTIVE", "DRAFT"})
    void test_nonActiveCurrencyRejected(com.paytech.platform.support.api.enums.ReferenceDataStatus status) {
        refreshCurrencies(ReferenceDataBuilders.currency(USD), ReferenceDataBuilders.currency("AED").status(status));
        var request = AccountingSetupRequestBuilder.ledgerBook(LedgerEntityType.OPERATING_ENTITY.getCode(), "AE001");
        request.setCurrency("AED");

        postAsOperator(PATH, request).assertRejected(LedgerSetupErrorCode.CURRENCY_NOT_ACTIVE);

        assertEquals(0, testMapper.countLedgerBooks());
    }

    /** Platform 停用币种后，刷新快照即拒绝新账簿；停用前开立的账簿不受影响。 */
    @Test
    void test_currencyDeactivatedInPlatformBlocksNewBooks() {
        refreshCurrencies(ReferenceDataBuilders.currency(USD), ReferenceDataBuilders.currency("AED"));
        var before = AccountingSetupRequestBuilder.ledgerBook(LedgerEntityType.OPERATING_ENTITY.getCode(), "AE001");
        before.setCurrency("AED");
        long bookId = postAsOperator(PATH, before).assertSuccess(201).id("ledgerBookId");

        refreshCurrencies(ReferenceDataBuilders.currency(USD), ReferenceDataBuilders.currency("AED")
            .status(com.paytech.platform.support.api.enums.ReferenceDataStatus.INACTIVE));
        var after = AccountingSetupRequestBuilder.ledgerBook(LedgerEntityType.OPERATING_ENTITY.getCode(), "AE002");
        after.setCurrency("AED");
        postAsOperator(PATH, after).assertRejected(LedgerSetupErrorCode.CURRENCY_NOT_ACTIVE);

        assertAll(() -> assertEquals(1, testMapper.countLedgerBooks()),
            () -> assertEquals(BookStatus.OPEN, ledgerBookMapper.selectById(bookId).getBookStatus()));
    }

    @Test
    void test_draftChartRejected() {
        ledgerMocker.mockChart("LOCAL", ReferenceDataStatus.DRAFT);
        var draftChart = AccountingSetupRequestBuilder.ledgerBook(LedgerEntityType.OPERATING_ENTITY.getCode(), "AE001");
        draftChart.setChartCode("LOCAL");

        postAsOperator(PATH, draftChart).assertRejected(LedgerSetupErrorCode.CHART_NOT_ACTIVE);
    }

    @Test
    void test_invalidEntityIdFormat() {
        postAsOperator(PATH, AccountingSetupRequestBuilder.ledgerBook(LedgerEntityType.OPERATING_ENTITY.getCode(), "AE000"))
            .assertInvalidRequest().assertDetailContains("主体标识格式非法");
        postAsOperator(PATH, AccountingSetupRequestBuilder.ledgerBook(LedgerEntityType.FINANCE_ENTITY.getCode(), "FE-1"))
            .assertInvalidRequest().assertDetailContains("主体标识格式非法");
    }
}
