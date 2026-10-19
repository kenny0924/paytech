package com.paytech.ledger.auto.ext.http.setup;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
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
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

/**
 * 创建账簿：OE/FE 主体、序号初始化、唯一性和前置资料校验。
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

    @Test
    void test_referenceDataMustBeActive() {
        var unknownCurrency = AccountingSetupRequestBuilder.ledgerBook(LedgerEntityType.OPERATING_ENTITY.getCode(),
            "AE001");
        unknownCurrency.setCurrency("EUR");
        postAsOperator(PATH, unknownCurrency).assertRejected(LedgerSetupErrorCode.CURRENCY_NOT_ACTIVE);

        var unknownChart = AccountingSetupRequestBuilder.ledgerBook(LedgerEntityType.OPERATING_ENTITY.getCode(),
            "AE001");
        unknownChart.setChartCode("NOT_EXISTS");
        postAsOperator(PATH, unknownChart).assertRejected(LedgerSetupErrorCode.CHART_NOT_ACTIVE);
    }

    @Test
    void test_inactiveReferenceDataRejected() {
        ledgerMocker.mockCurrency("AED", 2, CurrencySpecStatus.INACTIVE);
        ledgerMocker.mockChart("LOCAL", ReferenceDataStatus.DRAFT);
        var inactiveCurrency = AccountingSetupRequestBuilder.ledgerBook(LedgerEntityType.OPERATING_ENTITY.getCode(),
            "AE001");
        inactiveCurrency.setCurrency("AED");
        var draftChart = AccountingSetupRequestBuilder.ledgerBook(LedgerEntityType.OPERATING_ENTITY.getCode(), "AE001");
        draftChart.setChartCode("LOCAL");

        postAsOperator(PATH, inactiveCurrency).assertRejected(LedgerSetupErrorCode.CURRENCY_NOT_ACTIVE);
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
