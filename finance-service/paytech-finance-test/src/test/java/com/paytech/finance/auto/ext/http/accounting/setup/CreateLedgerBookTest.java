package com.paytech.finance.auto.ext.http.accounting.setup;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.finance.accounting.core.dal.dataobject.LedgerBook;
import com.paytech.finance.accounting.core.dal.mapper.LedgerBookMapper;
import com.paytech.finance.accounting.core.enums.BookStatus;
import com.paytech.finance.accounting.core.enums.FinanceEntityStatus;
import com.paytech.finance.accounting.core.enums.LedgerEntityType;
import com.paytech.finance.api.accounting.LedgerBookCommandApi;
import com.paytech.finance.api.accounting.request.CreateLedgerBookRequestBody;
import com.paytech.finance.test.base.MockTestBase;
import com.paytech.finance.test.builder.AccountingSetupRequestBuilder;
import com.paytech.finance.test.dal.AccountingTestMapper;
import com.paytech.finance.test.mocker.local.LedgerMocker;

/**
 * 创建账簿：OE/FE 主体、序号初始化、唯一性和前置资料校验。
 *
 * @see LedgerBookCommandApi#createLedgerBook(CreateLedgerBookRequestBody)
 */
class CreateLedgerBookTest extends MockTestBase {
    private static final String PATH = "/ledger-books/v1";

    @Autowired
    private LedgerBookMapper ledgerBookMapper;

    @Autowired
    private AccountingTestMapper testMapper;

    @Test
    void test_operatingEntityBookWithSequence() {
        var response = post(PATH, AccountingSetupRequestBuilder.ledgerBook(LedgerEntityType.OPERATING_ENTITY.getCode(),
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

        post(PATH, AccountingSetupRequestBuilder.ledgerBook(type, String.valueOf(active))).assertSuccess(201);
        post(PATH, AccountingSetupRequestBuilder.ledgerBook(type, String.valueOf(closed)))
            .assertBusinessRejected("财务主体不存在或已关闭");
        post(PATH, AccountingSetupRequestBuilder.ledgerBook(type, "123456789"))
            .assertBusinessRejected("财务主体不存在或已关闭");
    }

    @Test
    void test_duplicateBookRejected() {
        ledgerMocker.mockBook(LedgerEntityType.OPERATING_ENTITY, "AE001", "USD", LedgerMocker.STANDARD_CHART_ID);

        post(PATH, AccountingSetupRequestBuilder.ledgerBook(LedgerEntityType.OPERATING_ENTITY.getCode(), "AE001"))
            .assertBusinessRejected("账簿已存在");
    }

    @Test
    void test_referenceDataMustBeActive() {
        var unknownCurrency = AccountingSetupRequestBuilder.ledgerBook(LedgerEntityType.OPERATING_ENTITY.getCode(),
            "AE001");
        unknownCurrency.setCurrency("EUR");
        post(PATH, unknownCurrency).assertBusinessRejected("币种未启用: EUR");

        var unknownChart = AccountingSetupRequestBuilder.ledgerBook(LedgerEntityType.OPERATING_ENTITY.getCode(),
            "AE001");
        unknownChart.setChartCode("NOT_EXISTS");
        post(PATH, unknownChart).assertBusinessRejected("科目表不存在或未启用");
    }

    @Test
    void test_invalidEntityIdFormat() {
        post(PATH, AccountingSetupRequestBuilder.ledgerBook(LedgerEntityType.OPERATING_ENTITY.getCode(), "AE000"))
            .assertInvalidRequest().assertDetailContains("主体标识格式非法");
        post(PATH, AccountingSetupRequestBuilder.ledgerBook(LedgerEntityType.FINANCE_ENTITY.getCode(), "FE-1"))
            .assertInvalidRequest().assertDetailContains("主体标识格式非法");
    }
}
