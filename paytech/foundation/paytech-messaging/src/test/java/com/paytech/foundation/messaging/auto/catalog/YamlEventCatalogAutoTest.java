package com.paytech.foundation.messaging.auto.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.foundation.messaging.api.EventCatalogEntry;
import com.paytech.foundation.messaging.catalog.YamlEventCatalog;

/**
 * 事件目录读取与校验：打包的 v1 目录与各类非法登记。
 *
 * @see YamlEventCatalog#fromClasspath()
 * @see YamlEventCatalog#read(java.io.InputStream, java.util.Set)
 */
class YamlEventCatalogAutoTest {

    private static final String VALID_ENTRY = """
          - eventType: EconomicFactRecorded
            schemaVersion: 1
            producer: billing
            aggregateType: ECONOMIC_FACT
            aggregateVersionField: factVersion
            consumers: [ledger, account]
            strictOrder: false
            payloadSchemaRef: paytech-billing-api/event/EconomicFactRecorded/v1
        """;

    @Test
    void test_normalFlow_packagedCatalog() {
        YamlEventCatalog catalog = YamlEventCatalog.fromClasspath();

        assertEquals(List.of("PaymentSucceeded", "ClientReviewDecided", "EconomicFactRecorded", "AccountPosted",
            "SettlementRestrictionChanged"), catalog.entries().stream().map(EventCatalogEntry::eventType).toList());
        EventCatalogEntry payment = catalog.find("PaymentSucceeded").orElseThrow();
        assertEquals(2, payment.schemaVersion());
        assertEquals("PAYMENT_ATTEMPT", payment.aggregateType());
        assertEquals("payment", payment.producer());
        assertEquals(List.of("acquiring", "card"), payment.consumers());
        assertFalse(payment.strictOrder());
        EventCatalogEntry economic = catalog.find("EconomicFactRecorded").orElseThrow();
        assertEquals("billing", economic.producer());
        assertEquals(List.of("account"), economic.consumers());
        assertEquals(List.of("payout", "account"), catalog.find("SettlementRestrictionChanged").orElseThrow().consumers());
        EventCatalogEntry posted = catalog.find("AccountPosted").orElseThrow();
        assertEquals("account", posted.producer());
        assertEquals("ACCOUNT_POSTING", posted.aggregateType());
        assertEquals(List.of("ledger"), posted.consumers());
        assertTrue(posted.strictOrder());
        assertTrue(catalog.find("BalanceMoved").isEmpty());
        assertEquals(List.of("client-master"), catalog.find("ClientReviewDecided").orElseThrow().consumers());
        assertTrue(catalog.find("Unknown").isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCatalogs")
    void test_invalidParameter(String scenario, String yaml, String expectedMessage) {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> read(yaml), scenario);

        assertTrue(exception.getMessage().contains(expectedMessage), exception.getMessage());
    }

    static Stream<Arguments> invalidCatalogs() {
        return Stream.of(
            Arguments.of("缺少 events", "other: []\n", "events"),
            Arguments.of("eventType 重复", "events:\n" + VALID_ENTRY + VALID_ENTRY, "重复"),
            Arguments.of("producer 不是服务名", "events:\n" + VALID_ENTRY.replace("producer: billing",
                "producer: finance"), "finance"),
            Arguments.of("consumer 不是服务名", "events:\n" + VALID_ENTRY.replace("[ledger, account]",
                "[ledger, risk]"), "risk"),
            Arguments.of("consumers 含 producer", "events:\n" + VALID_ENTRY.replace("[ledger, account]",
                "[ledger, billing]"), "producer"),
            Arguments.of("consumers 重复", "events:\n" + VALID_ENTRY.replace("[ledger, account]",
                "[ledger, ledger]"), "重复"),
            Arguments.of("strictOrder 非布尔", "events:\n" + VALID_ENTRY.replace("strictOrder: false",
                "strictOrder: \"false\""), "strictOrder"),
            Arguments.of("schemaVersion 为 0", "events:\n" + VALID_ENTRY.replace("schemaVersion: 1",
                "schemaVersion: 0"), "schemaVersion"),
            Arguments.of("eventType 格式非法", "events:\n" + VALID_ENTRY.replace("eventType: EconomicFactRecorded",
                "eventType: economic.fact"), "eventType"),
            Arguments.of("未知字段", "events:\n" + VALID_ENTRY + "    partitions: 4\n", "未知字段"),
            Arguments.of("缺少 payloadSchemaRef", "events:\n" + VALID_ENTRY.replace(
                "    payloadSchemaRef: paytech-billing-api/event/EconomicFactRecorded/v1\n", ""), "payloadSchemaRef"));
    }

    private static YamlEventCatalog read(String yaml) {
        return YamlEventCatalog.read(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),
            YamlEventCatalog.SERVICE_NAMES);
    }
}
