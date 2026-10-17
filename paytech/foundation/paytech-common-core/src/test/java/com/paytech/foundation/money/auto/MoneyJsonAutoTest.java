package com.paytech.foundation.money.auto;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.foundation.money.Money;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 不依赖宿主全局开关的严格 Money JSON 契约。 */
class MoneyJsonAutoTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void test_canonicalSerializationAndRoundTrip() {
        Money value = Money.of("USD", "9007199254740993.0100");
        String json = mapper.writeValueAsString(value);
        Assertions.assertEquals("{\"currency\":\"USD\",\"amount\":\"9007199254740993.01\"}", json);
        Assertions.assertEquals(value, mapper.readValue(json, Money.class));
        Assertions.assertEquals("{\"currency\":\"USD\",\"amount\":\"0.00\"}",
            mapper.writeValueAsString(Money.of("USD", "-0.00")));
        Assertions.assertEquals("{\"currency\":\"USD\",\"amount\":\"1000.00\"}",
            mapper.writeValueAsString(Money.of("USD", new BigDecimal("1E+3"))));
    }

    @ParameterizedTest
    @CsvSource({"USD,10,10.00", "USD,10.00,10.00", "USD,1000,1000.00", "AED,0.1,0.10",
        "USD,-0.00,0.00", "USD,-10,-10.00", "JPY,10,10", "KWD,10,10.000", "KWD,0.001,0.001"})
    void test_jsonUsesMajorUnitsAndCurrencyFractionDigits(String currency, String input, String expected) {
        Money value = mapper.readValue("{\"currency\":\"" + currency + "\",\"amount\":\"" + input + "\"}", Money.class);
        Assertions.assertEquals(0, new BigDecimal(input).compareTo(value.getAmount()));
        Assertions.assertEquals(expected, value.toAmountString());
        String json = mapper.writeValueAsString(value);
        Assertions.assertEquals("{\"currency\":\"" + currency + "\",\"amount\":\"" + expected + "\"}", json);
        Assertions.assertEquals(value, mapper.readValue(json, Money.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "JPY", "KWD"})
    void test_currencyPaddingPreservesMaximumAmountRoundTrip(String currency) {
        Money value = Money.of(currency, "9".repeat(Money.MAX_PRECISION));
        String json = mapper.writeValueAsString(value);
        Assertions.assertEquals(value, mapper.readValue(json, Money.class));
    }

    @Test
    void test_orderIndependentNestedAndCollectionValues() {
        Money value = Money.of("AED", "-1.25");
        Assertions.assertEquals(value, mapper.readValue("{\"amount\":\"-1.25\",\"currency\":\"AED\"}", Money.class));
        Envelope envelope = new Envelope(value);
        Assertions.assertEquals(envelope, mapper.readValue(mapper.writeValueAsString(envelope), Envelope.class));
        List<Money> values = List.of(value, Money.zero("JPY"));
        Assertions.assertEquals(values,
            mapper.readValue(mapper.writeValueAsString(values), new TypeReference<List<Money>>() {}));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "1", "\"1\"", "[]", "{}", "{\"currency\":\"USD\"}", "{\"amount\":\"1\"}",
        "{\"currency\":null,\"amount\":\"1\"}", "{\"currency\":\"USD\",\"amount\":null}",
        "{\"currency\":\"USD\",\"amount\":1}", "{\"currency\":\"USD\",\"amount\":1.25}",
        "{\"currency\":123,\"amount\":\"1\"}", "{\"currency\":\"USD\",\"amount\":true}",
        "{\"currency\":\"USD\",\"amount\":{}}", "{\"currency\":\"USD\",\"amount\":\"1e3\"}",
        "{\"currency\":\"usd\",\"amount\":\"1\"}", "{\"currency\":\"USD\",\"amount\":\"1\",\"extra\":1}",
        "{\"currency\":\"USD\",\"amount\":\"1\",\"amount\":\"1\"}",
        "{\"currency\":\"USD\",\"currency\":\"AED\",\"amount\":\"1\"}",
        "{\"amount\":\"1\",\"currency\":\"USD\",\"\\u0061mount\":\"2\"}", "{\"currency\":\"ZZZ\",\"amount\":\"1\"}",
        "{\"currency\":\"USD\",\"amount\":\"0.0000000000000000001\"}",
        "{\"currency\":\"USD\",\"amount\":\"1.001\"}",
        "{\"currency\":\"JPY\",\"amount\":\"1.1\"}"})
    void test_invalidJsonIsRejected(String json) {
        Assertions.assertThrows(JacksonException.class, () -> mapper.readValue(json, Money.class));
    }

    @Test
    void test_explicitNestedNullIsRejected() {
        Assertions.assertThrows(JacksonException.class, () -> mapper.readValue("{\"money\":null}", Envelope.class));
    }

    record Envelope(Money money) {
    }
}
