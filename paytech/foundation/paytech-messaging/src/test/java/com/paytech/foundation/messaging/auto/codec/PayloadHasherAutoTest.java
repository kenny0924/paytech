package com.paytech.foundation.messaging.auto.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.messaging.core.codec.PayloadHasher;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Payload 规范哈希：独立向量、字段顺序无关、JSON Object Profile 边界。
 *
 * @see PayloadHasher#hash(ObjectNode)
 */
class PayloadHasherAutoTest {

    /** {"amount":"10.00","currency":"USD","factId":"7001"} 的 SHA-256，独立于实现用 shasum 计算。 */
    private static final String EXPECTED_HASH = "sha256:ee6b441d8a40b4a46c1ebd6e10f6be8f5629ecf79f30f4b3a22baf3aeef65da5";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void test_normalFlow_independentVectorAndKeyOrder() {
        ObjectNode ordered = MAPPER.createObjectNode().put("factId", "7001").put("amount", "10.00").put("currency", "USD");
        ObjectNode reordered = MAPPER.createObjectNode().put("currency", "USD").put("amount", "10.00").put("factId", "7001");

        assertEquals(EXPECTED_HASH, PayloadHasher.hash(ordered));
        assertEquals(EXPECTED_HASH, PayloadHasher.hash(reordered));
        assertNotEquals(EXPECTED_HASH, PayloadHasher.hash(ordered.deepCopy().put("amount", "11.00")));
    }

    @Test
    void test_invalidParameter_outsideJsonObjectProfile() {
        assertThrows(IllegalArgumentException.class, () -> PayloadHasher.hash(MAPPER.createObjectNode().put("rate", 1.5)));
        assertThrows(IllegalArgumentException.class, () -> PayloadHasher.hash(MAPPER.createObjectNode().put("delta", -1)));
    }
}
