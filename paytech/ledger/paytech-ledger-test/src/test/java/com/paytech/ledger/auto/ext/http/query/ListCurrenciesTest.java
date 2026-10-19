package com.paytech.ledger.auto.ext.http.query;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.ledger.api.LedgerSetupQueryApi;
import com.paytech.ledger.test.base.MockTestBase;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

import tools.jackson.databind.JsonNode;

/**
 * 币种规则列表：按币种代码排序、状态过滤与参数校验。
 *
 * @see LedgerSetupQueryApi#listCurrencies(String)
 */
class ListCurrenciesTest extends MockTestBase {

    @BeforeEach
    void setUpClassMocks() {
        ledgerMocker.mockCurrency("AED", 2);
        ledgerMocker.mockCurrency("KWD", 3, CurrencySpecStatus.DRAFT);
        ledgerMocker.mockCurrency("JPY", 0, CurrencySpecStatus.INACTIVE);
    }

    @Test
    void test_sortAndFilterByStatus() {
        assertEquals(List.of("AED", "JPY", "KWD", "USD"), currencies("/currencies/v1"));
        assertEquals(List.of("AED", "USD"), currencies("/currencies/v1?status=ACTIVE"));
        assertEquals(List.of("KWD"), currencies("/currencies/v1?status=DRAFT"));
        assertEquals(List.of("JPY"), currencies("/currencies/v1?status=INACTIVE"));

        JsonNode usd = items("/currencies/v1?status=ACTIVE").get(1);
        assertAll(() -> assertEquals(2, usd.path("minorUnit").asInt()),
            () -> assertEquals("HALF_UP", usd.path("roundingMode").asString()),
            () -> assertEquals(CurrencySpecStatus.ACTIVE.getCode(), usd.path("status").asString()));
    }

    @Test
    void test_invalidParameter() {
        get("/currencies/v1?status=CLOSED").assertStatus(400);
    }

    /** 读取币种代码列表。 */
    List<String> currencies(String path) {
        return items(path).stream().map(item -> item.path("currency").asString()).toList();
    }

    /** 列表响应的 items。 */
    List<JsonNode> items(String path) {
        List<JsonNode> items = new ArrayList<>();
        get(path).assertSuccess(200).json().path("items").forEach(items::add);
        return items;
    }
}
