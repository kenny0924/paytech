package com.paytech.billing.test.checker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactOriginType;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.api.enums.SourceRuleType;
import com.paytech.billing.core.enums.BillingCodes;
import com.paytech.billing.domain.OrderPaymentSucceededFact;
import com.paytech.billing.test.dal.BillingTestMapper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 自动 PAYMENT 事实的数据库只读断言：事实头、引用、明细行、计算证据与定价输入
 * <li>创建时断言该支付尝试恰有一条 acquiring 来源事实；预期值来自用例约定，不重新调用被测算法。</li>
 */
public class PaymentFactChecker {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final BillingTestMapper mapper;

    private final Map<String, Object> fact;

    private final long factId;

    /** 读取该支付尝试的唯一事实。 */
    public PaymentFactChecker(BillingTestMapper mapper, long paymentAttemptId) {
        List<Map<String, Object>> facts = mapper.selectFactsBySource(OrderPaymentSucceededFact.SOURCE_SYSTEM,
            OrderPaymentSucceededFact.SOURCE_EVENT_KEY_PREFIX + paymentAttemptId);
        assertEquals(1, facts.size(), "支付尝试应恰有一条计费事实: " + paymentAttemptId);
        this.mapper = mapper;
        this.fact = facts.getFirst();
        this.factId = ((Number)fact.get("id")).longValue();
    }

    /** 事实 ID。 */
    public long factId() {
        return factId;
    }

    /** PAYMENT / AUTO / APPROVED / 版本 1，无编制与复核人，登记时间已写入。 */
    public PaymentFactChecker autoApproved(long sourceVersion) {
        assertAll("自动事实状态", () -> assertEquals(BillingFactType.PAYMENT.getCode(), fact.get("fact_type")),
            () -> assertEquals(FactOriginType.AUTO.getCode(), fact.get("origin_type")),
            () -> assertEquals(BillingFactStatus.APPROVED.getCode(), fact.get("status")),
            () -> assertEquals(1L, number(fact.get("fact_version"))),
            () -> assertEquals(sourceVersion, number(fact.get("source_version"))),
            () -> assertNull(fact.get("maker_id")), () -> assertNull(fact.get("checker_id")),
            () -> assertNotNull(fact.get("approved_time")),
            () -> assertTrue(((String)fact.get("source_payload_hash")).matches("[0-9a-f]{64}")));
        return this;
    }

    /** 主体、币种与毛额。 */
    public PaymentFactChecker header(long clientId, String entityNo, String currency, String grossAmount,
        Long localPartnerId, Long financeEntityId) {
        assertAll("事实头", () -> assertEquals(clientId, number(fact.get("client_id"))),
            () -> assertEquals(entityNo, fact.get("entity_no")), () -> assertEquals(currency, fact.get("currency")),
            () -> assertAmount(grossAmount, fact.get("gross_amount"), "gross_amount"),
            () -> assertEquals(localPartnerId, nullableNumber(fact.get("local_partner_id"))),
            () -> assertEquals(financeEntityId, nullableNumber(fact.get("finance_entity_id"))));
        return this;
    }

    /** 业务时间与到期转可用时间。 */
    public PaymentFactChecker times(Instant businessTime, Instant balanceAvailableTime) {
        assertAll("事实时间", () -> assertEquals(businessTime, instant(fact.get("business_time"))),
            () -> assertEquals(balanceAvailableTime, instant(fact.get("balance_available_time"))));
        return this;
    }

    /** 到期转可用时间。 */
    public PaymentFactChecker balanceAvailableTime(Instant expected) {
        assertEquals(expected, instant(fact.get("balance_available_time")), "balance_available_time");
        return this;
    }

    /** 引用恰为给定集合；渠道交易引用带外部交易号类型与交易号，其他引用不带。 */
    public PaymentFactChecker refs(Map<FactRefType, String> expected, String externalReferenceType,
        String externalTransactionId) {
        List<Map<String, Object>> refs = mapper.selectBillingFactRefs(factId);
        assertEquals(expected.size(), refs.size(), "引用数量");
        for (Map<String, Object> ref : refs) {
            FactRefType type = BillingCodes.parse(FactRefType.class, "引用类型", (String)ref.get("ref_type"));
            boolean channelRef = type == FactRefType.CHANNEL_TRANSACTION_REF;
            assertAll("引用 " + type, () -> assertEquals(expected.get(type), ref.get("ref_id")),
                () -> assertEquals(channelRef ? externalReferenceType : null, ref.get("external_reference_type")),
                () -> assertEquals(channelRef ? externalTransactionId : null, ref.get("external_transaction_id")));
        }
        return this;
    }

    /** 明细行数量。 */
    public PaymentFactChecker lineCount(int expected) {
        assertEquals(expected, lines().size(), "明细行数量");
        return this;
    }

    /** 某行的组件、付方、收方与记账金额（CHARGE、无目标余额桶）。 */
    public PaymentFactChecker line(int lineNo, FactComponentType component, FactPartyRole payerRole, String payerId,
        FactPartyRole payeeRole, String payeeId, String postedAmount) {
        Map<String, Object> line = line(lineNo);
        assertAll("明细行 " + lineNo, () -> assertEquals(component.getCode(), line.get("component_type")),
            () -> assertEquals(payerRole.getCode(), line.get("payer_role")), () -> assertEquals(payerId, line.get("payer_id")),
            () -> assertEquals(payeeRole.getCode(), line.get("payee_role")), () -> assertEquals(payeeId, line.get("payee_id")),
            () -> assertAmount(postedAmount, line.get("posted_amount"), "posted_amount"),
            () -> assertEquals(FactLineDirection.CHARGE.getCode(), line.get("effect_direction")),
            () -> assertNull(line.get("target_bucket")));
        return this;
    }

    /** 某行的计算证据列：基数、费率、原始金额与规则来源。 */
    public PaymentFactChecker evidence(int lineNo, String basisAmount, String rateValue, String rawAmount,
        SourceRuleType ruleType, long ruleVersionId, Long priceId, Long componentId) {
        Map<String, Object> line = line(lineNo);
        assertAll("明细行证据 " + lineNo, () -> assertAmount(basisAmount, line.get("basis_amount"), "basis_amount"),
            () -> assertAmount(rateValue, line.get("rate_value"), "rate_value"),
            () -> assertAmount(rawAmount, line.get("raw_amount"), "raw_amount"),
            () -> assertEquals(ruleType.getCode(), line.get("source_rule_type")),
            () -> assertEquals(ruleVersionId, number(line.get("source_rule_version_id"))),
            () -> assertEquals(priceId, nullableNumber(line.get("source_price_id"))),
            () -> assertEquals(componentId, nullableNumber(line.get("source_rule_component_id"))));
        return this;
    }

    /** 某行计算证据 JSON 中的字段值（字符串或数字文本；null 表示该字段为 JSON null）。 */
    public PaymentFactChecker evidenceField(int lineNo, String field, String expected) {
        JsonNode evidence = JSON.readTree(String.valueOf(line(lineNo).get("source_evidence")));
        assertTrue(evidence.has(field), "证据缺少字段 " + field);
        JsonNode value = evidence.get(field);
        assertEquals(expected, value.isNull() ? null : value.asString(), "证据字段 " + field);
        return this;
    }

    /** 定价输入的一列（数字列按文本比较）。 */
    public PaymentFactChecker pricing(String column, Object expected) {
        Map<String, Object> pricing = mapper.selectFactPricing(factId);
        assertNotNull(pricing, "缺少定价输入");
        Object actual = pricing.get(column);
        if (actual instanceof LocalDateTime time) {
            actual = time.toInstant(ZoneOffset.UTC);
        }
        assertEquals(expected == null ? null : expected.toString(), actual == null ? null : actual.toString(),
            "定价输入 " + column);
        return this;
    }

    /** 某一行。 */
    Map<String, Object> line(int lineNo) {
        return lines().stream().filter(line -> number(line.get("line_no")) == lineNo).findFirst()
            .orElseThrow(() -> new AssertionError("缺少明细行 " + lineNo));
    }

    /** 全部明细行。 */
    List<Map<String, Object>> lines() {
        return mapper.selectFactLineDetails(factId);
    }

    /** 金额等值比较；期望为空时实际必须为空。 */
    static void assertAmount(String expected, Object actual, String column) {
        if (expected == null) {
            assertNull(actual, column);
            return;
        }
        assertNotNull(actual, column);
        assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal)actual),
            column + " 期望 " + expected + " 实际 " + actual);
    }

    /** 数值列。 */
    static long number(Object value) {
        return ((Number)Objects.requireNonNull(value)).longValue();
    }

    /** 可空数值列。 */
    static Long nullableNumber(Object value) {
        return value == null ? null : ((Number)value).longValue();
    }

    /** DATETIME 列（UTC）。 */
    static Instant instant(Object value) {
        if (value instanceof LocalDateTime time) {
            return time.toInstant(ZoneOffset.UTC);
        }
        return value == null ? null : (Instant)value;
    }
}
