package com.paytech.foundation.messaging.auto.consume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventConsumer;
import com.paytech.foundation.messaging.api.DomainEventHandler;
import com.paytech.foundation.messaging.api.EventCatalog;
import com.paytech.foundation.messaging.catalog.YamlEventCatalog;
import com.paytech.foundation.messaging.consume.DomainEventHandlerRegistry;

/**
 * Handler 注册表按事件目录的启动期校验。
 *
 * @see DomainEventHandlerRegistry#DomainEventHandlerRegistry(String, List, EventCatalog)
 */
class DomainEventHandlerRegistryAutoTest {

    private static final EventCatalog CATALOG = YamlEventCatalog.read(new ByteArrayInputStream("""
        events:
          - eventType: EconomicFactRecorded
            schemaVersion: 1
            producer: billing
            aggregateType: ECONOMIC_FACT
            aggregateVersionField: factVersion
            consumers: [ledger, account]
            strictOrder: false
            payloadSchemaRef: ref
          - eventType: BalanceMoved
            schemaVersion: 1
            producer: account
            aggregateType: ACCOUNT_BOOK
            aggregateVersionField: movementSequence
            consumers: [ledger]
            strictOrder: true
            payloadSchemaRef: ref
        """.getBytes(StandardCharsets.UTF_8)), YamlEventCatalog.SERVICE_NAMES);

    @Test
    void test_normalFlow() {
        DomainEventHandlerRegistry registry = new DomainEventHandlerRegistry("ledger",
            List.of(new EconomicFactHandler(), new BalanceMovedHandler()), CATALOG);

        assertEquals(Set.of("account", "billing"), registry.producers());
        DomainEventHandlerRegistry.Registration balance = registry.find("BalanceMoved").orElseThrow();
        assertEquals("account", balance.producer());
        assertTrue(balance.strictOrder());
        assertTrue(registry.find("PaymentSucceeded").isEmpty());
    }

    @Test
    void test_invalidParameter_missingAnnotation() {
        DomainEventConsumer unannotated = event -> { };

        assertRejected("缺少 @DomainEventHandler", "ledger", unannotated);
    }

    @Test
    void test_businessRuleViolation_eventNotInCatalog() {
        assertRejected("未登记", "ledger", new UnknownEventHandler());
    }

    @Test
    void test_businessRuleViolation_serviceNotRegisteredConsumer() {
        assertRejected("未登记本服务为消费者", "account", new BalanceMovedHandler());
    }

    @Test
    void test_businessRuleViolation_strictOrderMismatch() {
        assertRejected("strictOrder", "ledger", new StrictEconomicFactHandler());
    }

    @Test
    void test_businessRuleViolation_duplicateHandler() {
        assertRejected("只能有一个", "ledger", new EconomicFactHandler(), new EconomicFactHandler());
    }

    private static void assertRejected(String expectedMessage, String consumer, DomainEventConsumer... handlers) {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> new DomainEventHandlerRegistry(consumer, List.of(handlers), CATALOG));
        assertTrue(exception.getMessage().contains(expectedMessage), exception.getMessage());
    }

    @DomainEventHandler(eventType = "EconomicFactRecorded", strictOrder = false)
    static final class EconomicFactHandler implements DomainEventConsumer {
        @Override
        public void consume(DomainEvent event) {
        }
    }

    @DomainEventHandler(eventType = "EconomicFactRecorded", strictOrder = true)
    static final class StrictEconomicFactHandler implements DomainEventConsumer {
        @Override
        public void consume(DomainEvent event) {
        }
    }

    @DomainEventHandler(eventType = "BalanceMoved", strictOrder = true)
    static final class BalanceMovedHandler implements DomainEventConsumer {
        @Override
        public void consume(DomainEvent event) {
        }
    }

    @DomainEventHandler(eventType = "PaymentRefunded", strictOrder = false)
    static final class UnknownEventHandler implements DomainEventConsumer {
        @Override
        public void consume(DomainEvent event) {
        }
    }
}
