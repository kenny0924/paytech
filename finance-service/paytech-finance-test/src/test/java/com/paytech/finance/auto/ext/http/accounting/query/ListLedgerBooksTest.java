package com.paytech.finance.auto.ext.http.accounting.query;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.finance.accounting.core.enums.BookStatus;
import com.paytech.finance.accounting.core.enums.FinanceEntityStatus;
import com.paytech.finance.accounting.core.enums.LedgerEntityType;
import com.paytech.finance.api.accounting.LedgerBookQueryApi;
import com.paytech.finance.test.base.MockTestBase;
import com.paytech.finance.test.mocker.local.LedgerMocker;

import tools.jackson.databind.JsonNode;

/**
 * 账簿列表：主体与币种过滤、分页总数与稳定排序、参数校验。
 *
 * @see LedgerBookQueryApi#listLedgerBooks(String, String, String, Integer, Integer)
 */
class ListLedgerBooksTest extends MockTestBase {
    private static final String PATH = "/ledger-books/v1";

    private long usdBook;

    private long feBook;

    @BeforeEach
    void setUpClassMocks() {
        usdBook = ledgerMocker.mockBook(LedgerEntityType.OPERATING_ENTITY, "AE001", "USD", LedgerMocker.STANDARD_CHART_ID);
        ledgerMocker.mockBook(LedgerEntityType.OPERATING_ENTITY, "AE001", "AED", LedgerMocker.STANDARD_CHART_ID);
        ledgerMocker.mockBook(LedgerEntityType.OPERATING_ENTITY, "SA001", "USD", LedgerMocker.STANDARD_CHART_ID);
        long fe = ledgerMocker.mockFinanceEntity("FE-PLATFORM", 880001L, FinanceEntityStatus.ACTIVE);
        feBook = ledgerMocker.mockBook(LedgerEntityType.FINANCE_ENTITY, String.valueOf(fe), "USD",
            LedgerMocker.STANDARD_CHART_ID);
    }

    @Test
    void test_filterByEntityAndCurrency() {
        List<JsonNode> byEntity = get(PATH + "?entityType=OPERATING_ENTITY&entityId=AE001").assertSuccess(200)
            .assertPage(1, 10, 2);
        assertEquals(List.of("USD", "AED"), byEntity.stream().map(item -> item.path("currency").asString()).toList());

        JsonNode only = get(PATH + "?entityId=AE001&currency=USD").assertSuccess(200).assertPage(1, 10, 1).getFirst();
        assertAll(() -> assertEquals(String.valueOf(usdBook), only.path("ledgerBookId").asString()),
            () -> assertEquals(LedgerEntityType.OPERATING_ENTITY.getCode(), only.path("entityType").asString()),
            () -> assertEquals("STANDARD", only.path("chartCode").asString()),
            () -> assertEquals(BookStatus.OPEN.getCode(), only.path("bookStatus").asString()));

        JsonNode fe = get(PATH + "?entityType=FINANCE_ENTITY").assertSuccess(200).assertPage(1, 10, 1).getFirst();
        assertEquals(String.valueOf(feBook), fe.path("ledgerBookId").asString());
        get(PATH + "?currency=USD").assertSuccess(200).assertPage(1, 10, 3);
    }

    @Test
    void test_pagingTotalAndStableOrder() {
        for (int i = 10; i < 21; i++) {
            ledgerMocker.mockBook(LedgerEntityType.OPERATING_ENTITY, "QA0" + i, "USD", LedgerMocker.STANDARD_CHART_ID);
        }

        List<JsonNode> first = get(PATH + "?page=1&size=10").assertSuccess(200).assertPage(1, 10, 15);
        List<JsonNode> second = get(PATH + "?page=2&size=10").assertSuccess(200).assertPage(2, 10, 15);

        assertEquals(10, first.size());
        assertEquals(5, second.size());
        List<Long> ids = Stream.concat(first.stream(), second.stream())
            .map(item -> Long.parseLong(item.path("ledgerBookId").asString())).toList();
        assertEquals(ids.stream().sorted().toList(), ids, "按主键升序");
        assertEquals(15, ids.stream().distinct().count());
    }

    @Test
    void test_invalidParameter() {
        get(PATH + "?size=7").assertInvalidRequest();
        get(PATH + "?page=0").assertInvalidRequest();
        get(PATH + "?entityType=CLIENT").assertStatus(400);
    }
}
