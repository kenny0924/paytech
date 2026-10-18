package com.paytech.foundation.messaging.api.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.foundation.messaging.api.AggregateKey;

/**
 * 聚合身份与补偿事件 {@code event_key} 的生成、解析和格式边界。
 *
 * @see AggregateKey#eventKey(String, long)
 * @see AggregateKey#parseEventKey(String)
 * @see AggregateKey#fromEventKey(String)
 */
class AggregateKeyAutoTest {

    @Test
    void test_normalFlow() {
        AggregateKey key = AggregateKey.of("LEDGER_BOOK", "123456");

        String eventKey = key.eventKey("EconomicFactRecorded", 8871);

        assertEquals("LEDGER_BOOK:123456:EconomicFactRecorded:8871", eventKey);
        AggregateKey.ParsedEventKey parsed = AggregateKey.parseEventKey(eventKey);
        assertEquals(key, parsed.aggregateKey());
        assertEquals("EconomicFactRecorded", parsed.eventType());
        assertEquals(8871, parsed.aggregateVersion());
        assertEquals(key, AggregateKey.fromEventKey(eventKey));
    }

    @Test
    void test_edgeCase_maximumLengthFitsCompensationEventKey() {
        AggregateKey key = AggregateKey.of("A".repeat(32), "9".repeat(40));

        String eventKey = key.eventKey("E" + "a".repeat(31), Long.MAX_VALUE);

        assertTrue(eventKey.length() <= 128, () -> "length=" + eventKey.length());
        assertEquals(Long.MAX_VALUE, AggregateKey.parseEventKey(eventKey).aggregateVersion());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ledger_book", "1BOOK", "BOOK-1", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void test_invalidParameter_aggregateType(String aggregateType) {
        assertThrows(IllegalArgumentException.class, () -> AggregateKey.of(aggregateType, "1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a:b", "-1", "id with space", "12345678901234567890123456789012345678901"})
    void test_invalidParameter_aggregateId(String aggregateId) {
        assertThrows(IllegalArgumentException.class, () -> AggregateKey.of("BOOK", aggregateId));
    }

    @Test
    void test_invalidParameter_eventTypeAndVersion() {
        AggregateKey key = AggregateKey.of("BOOK", "1");

        assertThrows(IllegalArgumentException.class, () -> key.eventKey("economic_fact", 1));
        assertThrows(IllegalArgumentException.class, () -> key.eventKey("EconomicFact", -1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"BOOK:1:Fact", "BOOK:1:Fact:1:2", "BOOK:1:Fact:01", "BOOK:1:Fact:-1", "BOOK:1:Fact:١",
        "BOOK:1:Fact:99999999999999999999", "book:1:Fact:1"})
    void test_invalidParameter_eventKey(String eventKey) {
        assertThrows(IllegalArgumentException.class, () -> AggregateKey.parseEventKey(eventKey));
    }
}
