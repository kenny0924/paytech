package com.paytech.ledger.auto.ext.http.query;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.ledger.api.LedgerSetupQueryApi;
import com.paytech.ledger.core.enums.FinanceEntityStatus;
import com.paytech.ledger.test.base.MockTestBase;

import tools.jackson.databind.JsonNode;

/**
 * 财务主体列表：状态过滤、字段投影、分页总数与主键升序、参数校验。
 *
 * @see LedgerSetupQueryApi#listFinanceEntities(String, Integer, Integer)
 */
class ListFinanceEntitiesTest extends MockTestBase {
    private static final String PATH = "/finance-entities/v1";

    private long activeEntity;

    @BeforeEach
    void setUpClassMocks() {
        activeEntity = ledgerMocker.mockFinanceEntity("FE-PLATFORM", 880001L, FinanceEntityStatus.ACTIVE);
        ledgerMocker.mockFinanceEntity("FE-CLOSED", 880002L, FinanceEntityStatus.CLOSED);
    }

    @Test
    void test_filterByStatus() {
        get(PATH).assertSuccess(200).assertPage(1, 10, 2);

        JsonNode active = get(PATH + "?status=ACTIVE").assertSuccess(200).assertPage(1, 10, 1).getFirst();
        assertAll(() -> assertEquals(String.valueOf(activeEntity), active.path("financeEntityId").asString()),
            () -> assertEquals("FE-PLATFORM", active.path("entityCode").asString()),
            () -> assertEquals("880001", active.path("legalPartyId").asString()),
            () -> assertEquals("Test FE-PLATFORM", active.path("displayName").asString()),
            () -> assertEquals(FinanceEntityStatus.ACTIVE.getCode(), active.path("status").asString()),
            () -> assertFalse(active.path("createTime").asString().isBlank()));

        JsonNode closed = get(PATH + "?status=CLOSED").assertSuccess(200).assertPage(1, 10, 1).getFirst();
        assertEquals("FE-CLOSED", closed.path("entityCode").asString());
    }

    @Test
    void test_pagingTotalAndStableOrder() {
        for (int i = 0; i < 9; i++) {
            ledgerMocker.mockFinanceEntity("FE-PAGE-" + i, 881000L + i, FinanceEntityStatus.ACTIVE);
        }

        List<JsonNode> first = get(PATH + "?page=1&size=10").assertSuccess(200).assertPage(1, 10, 11);
        List<JsonNode> second = get(PATH + "?page=2&size=10").assertSuccess(200).assertPage(2, 10, 11);

        assertEquals(1, second.size());
        List<Long> ids = Stream.concat(first.stream(), second.stream())
            .map(item -> Long.parseLong(item.path("financeEntityId").asString())).toList();
        assertEquals(ids.stream().sorted().toList(), ids, "按主键升序");
        assertEquals(11, ids.stream().distinct().count());
    }

    @Test
    void test_invalidParameter() {
        get(PATH + "?size=7").assertInvalidRequest();
        get(PATH + "?page=0").assertInvalidRequest();
        get(PATH + "?status=DRAFT").assertStatus(400);
    }
}
