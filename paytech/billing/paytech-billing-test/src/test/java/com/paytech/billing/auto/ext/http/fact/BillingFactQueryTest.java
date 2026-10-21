package com.paytech.billing.auto.ext.http.fact;

import static com.paytech.billing.test.builder.BillingFactRequestBuilder.CHANNEL_ID;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.CLIENT_ID;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.CURRENCY;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.ENTITY_NO;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.FINANCE_ENTITY_ID;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.LOCAL_PARTNER_ID;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.OTHER_ENTITY_NO;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.paytech.billing.api.BillingFactQueryApi;
import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactOriginType;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.api.enums.LineLinkType;
import com.paytech.billing.api.enums.SourceRuleType;
import com.paytech.billing.domain.BillingFactBookScope;
import com.paytech.billing.test.base.AbstractBillingFactApiTest;
import com.paytech.billing.test.builder.BillingFactRequestBuilder;
import com.paytech.billing.test.checker.BillingResponseChecker;

import tools.jackson.databind.JsonNode;

/**
 * 计费事实读取：按 ID（含规则行证据与行链接）、按账簿主体游标分页（account 重放）、发布进度（关账一致性检查）与按外部引用查询
 * <li>OE 账簿按 entityNo、Finance Entity 账簿按 financeEntityId 匹配并限定币种，只返回 APPROVED 与 SUPERSEDED；按引用返回任意状态；
 * 两者都按 factId 升序游标分页，满页返回下一页游标。</li>
 *
 * @see BillingFactQueryApi#getBillingFact(String)
 * @see BillingFactQueryApi#listBillingFacts(String, String, String, String, String)
 * @see BillingFactQueryApi#getBillingFactProgress(String, String, String)
 * @see BillingFactQueryApi#listBillingFactsByRef(String, String, String, String)
 */
class BillingFactQueryTest extends AbstractBillingFactApiTest {
    /** 前置 PAYMENT 事实 ID。 */
    private static final long PAYMENT_FACT_ID = 2_100_001L;

    /** 前置 REFUND 事实 ID。 */
    private static final long REFUND_FACT_ID = 2_100_002L;

    /** 前置 PAYMENT 事实的渠道交易引用 ID。 */
    static final String CHANNEL_TRANSACTION_REF_ID = "7300001";

    /** 渠道外部交易号类型（Payment 渠道交易引用类型目录，billing 不持有该枚举）。 */
    static final String EXTERNAL_REFERENCE_TYPE = "PAY";

    /** 渠道外部交易号。 */
    static final String EXTERNAL_TRANSACTION_ID = "NI-TXN-0001";

    /** 分页：DRAFT 与其他 OE 的事实不出现；limit 1 逐页读取，满页返回下一页游标，末页游标为空；Finance Entity 账簿只含合作方事实。 */
    @Test
    void test_listByBookPagination() {
        long first = submitApprovedAndPublished(BillingFactRequestBuilder.template());
        long partner = submitApprovedAndPublished(BillingFactRequestBuilder.template().partner().addLine(
            FactComponentType.PLATFORM_SERVICE_CHARGE, FactPartyRole.LOCAL_PARTNER, LOCAL_PARTNER_ID,
            FactPartyRole.FINANCE_ENTITY, FINANCE_ENTITY_ID, "1.00", FactLineDirection.CHARGE));
        submitDraft(BillingFactRequestBuilder.template());
        submitApprovedAndPublished(BillingFactRequestBuilder.template().entityNo(OTHER_ENTITY_NO).clearLines().addLine(
            FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, OTHER_ENTITY_NO, FactPartyRole.CLIENT,
            CLIENT_ID, "1.00", FactLineDirection.CHARGE));

        List<String> ids = collectPages(cursor -> list(BillingFactBookScope.OPERATING_ENTITY, ENTITY_NO, cursor, "1"));
        JsonNode financeBook =
            list(BillingFactBookScope.FINANCE_ENTITY, FINANCE_ENTITY_ID, null, null).assertSuccess(200).json();

        assertAll(() -> assertEquals(List.of(Long.toString(first), Long.toString(partner)), ids),
            () -> assertEquals(1, financeBook.path("items").size()),
            () -> assertEquals(Long.toString(partner), financeBook.path("items").get(0).path("factId").asString()),
            () -> assertEquals(2, financeBook.path("items").get(0).path("lines").size()),
            () -> assertEquals(1, financeBook.path("items").get(0).path("refs").size()),
            () -> assertTrue(absent(financeBook.path("nextFromFactId")), "末页游标应为空"));
    }

    /** 账簿主体类型与标识不匹配、limit 超出 1–500、主体类型不在目录都是参数错误。 */
    @Test
    void test_listByBookInvalidParameter() {
        list(BillingFactBookScope.OPERATING_ENTITY, FINANCE_ENTITY_ID, null, null).assertInvalidRequest();
        list(BillingFactBookScope.FINANCE_ENTITY, ENTITY_NO, null, null).assertInvalidRequest();
        list(BillingFactBookScope.OPERATING_ENTITY, ENTITY_NO, null, "501").assertInvalidRequest();
        list("CLIENT", CLIENT_ID, null, null).assertInvalidRequest();
        list(BillingFactBookScope.OPERATING_ENTITY, ENTITY_NO, null, "500").assertSuccess(200);
    }

    /** 发布进度：已批准未发布的事实计入 pending 与最大未发布 ID，已发布（含已被替代）事实计入已发布数并给出最大已发布 ID；DRAFT 不计入。 */
    @Test
    void test_progressCounts() {
        long published = submitApprovedAndPublished(BillingFactRequestBuilder.template());
        submitDraft(BillingFactRequestBuilder.template());
        long unpublished = published + 1_000_000L;
        insertFact(unpublished, BillingFactType.MANUAL_ADJUSTMENT, BillingFactStatus.APPROVED, null);
        insertFact(unpublished + 1, BillingFactType.MANUAL_ADJUSTMENT, BillingFactStatus.SUPERSEDED, Instant.now());

        progress(BillingFactBookScope.OPERATING_ENTITY, ENTITY_NO).assertSuccess(200).assertText("pendingCount", "1")
            .assertText("maxPendingFactId", Long.toString(unpublished)).assertText("publishedFactCount", "2")
            .assertText("maxPublishedFactId", Long.toString(unpublished + 1));
        progress(BillingFactBookScope.FINANCE_ENTITY, FINANCE_ENTITY_ID).assertSuccess(200)
            .assertText("pendingCount", "0").assertText("maxPendingFactId", null).assertText("publishedFactCount", "0")
            .assertText("maxPublishedFactId", null);
    }

    /**
     * 按 ID：规则行返回费率、基数、原始金额、规则族与 JSON 计算证据；行链接只出现在以其明细行为新行的事实上
     * <li>引用按类型升序：CHANNEL_TRANSACTION_REF 带渠道外部交易号两项，ORDER 两项为空。</li>
     */
    @Test
    void test_getFactWithRuleEvidenceAndLinks() {
        insertPaymentWithRefundLink();

        JsonNode payment = getFact(PAYMENT_FACT_ID).assertSuccess(200)
            .assertText("originType", FactOriginType.AUTO.getCode()).json();
        JsonNode refund = getFact(REFUND_FACT_ID).assertSuccess(200).json();
        JsonNode line = payment.path("lines").get(0);
        JsonNode link = refund.path("links").get(0);
        JsonNode channelRef = payment.path("refs").get(0);
        JsonNode orderRef = payment.path("refs").get(1);
        assertAll(() -> assertEquals(2, payment.path("refs").size()),
            () -> assertEquals(FactRefType.CHANNEL_TRANSACTION_REF.getCode(), channelRef.path("refType").asString()),
            () -> assertEquals(CHANNEL_TRANSACTION_REF_ID, channelRef.path("refId").asString()),
            () -> assertEquals(EXTERNAL_REFERENCE_TYPE, channelRef.path("externalReferenceType").asString()),
            () -> assertEquals(EXTERNAL_TRANSACTION_ID, channelRef.path("externalTransactionId").asString()),
            () -> assertEquals(FactRefType.ORDER.getCode(), orderRef.path("refType").asString()),
            () -> assertTrue(absent(orderRef.path("externalReferenceType"))),
            () -> assertTrue(absent(orderRef.path("externalTransactionId"))),
() -> assertEquals(FactComponentType.CHANNEL_COST.getCode(), line.path("componentType").asString()),
            () -> assertEquals("0.029", line.path("rateValue").asString()),
            () -> assertEquals("100.00000000", line.path("basisAmount").asString()),
            () -> assertEquals("2.90000000", line.path("rawAmount").asString()),
            () -> assertEquals("2.90", line.path("postedAmount").asString()),
            () -> assertEquals(SourceRuleType.CHANNEL_COST.getCode(), line.path("sourceRuleType").asString()),
            () -> assertEquals("8001", line.path("sourceRuleVersionId").asString()),
            () -> assertEquals("8002", line.path("sourceRuleComponentId").asString()),
            () -> assertEquals("PROCESSING", line.path("sourceEvidence").path("componentCode").asString()),
            () -> assertEquals("1", line.path("sourceEvidence").path("schemaVersion").asString()),
            () -> assertEquals(0, payment.path("links").size()), () -> assertEquals(1, refund.path("links").size()),
            () -> assertEquals("2100011", link.path("sourceLineId").asString()),
            () -> assertEquals("2100021", link.path("targetLineId").asString()),
            () -> assertEquals(LineLinkType.REVERSAL.getCode(), link.path("linkType").asString()),
            () -> assertEquals("2.90", link.path("linkedAmount").asString()),
            () -> assertEquals(Long.toString(PAYMENT_FACT_ID), refund.path("parentFactId").asString()));
    }

    /** 按 ID：不存在的事实业务拒绝。 */
    @Test
    void test_getFactNotFound() {
        getFact(9_999_999L).assertBusinessRejected("计费事实不存在");
    }

    /** 按引用：命中任意状态事实，按 factId 升序游标分页；其他引用值与其他类型不出现；跨类型引用（ORDER）同样命中。 */
    @Test
    void test_listByRefPagination() {
        String ticket = "OPS-SHARED-1";
        long draft = submitDraft(BillingFactRequestBuilder.template().clearRefs().ref(FactRefType.TICKET, ticket));
        long approved = submitApprovedAndPublished(
            BillingFactRequestBuilder.template().clearRefs().ref(FactRefType.TICKET, ticket));
        long third = submitDraft(BillingFactRequestBuilder.template().clearRefs().ref(FactRefType.TICKET, ticket));
        submitDraft(BillingFactRequestBuilder.template().clearRefs().ref(FactRefType.TICKET, "OPS-OTHER"));
        insertPaymentWithRefundLink();

        List<String> ids = collectPages(cursor -> byRef(FactRefType.TICKET.getCode(), ticket, cursor, "2"));
        JsonNode first = byRef(FactRefType.TICKET.getCode(), ticket, null, "2").assertSuccess(200).json();
        JsonNode order = byRef(FactRefType.ORDER.getCode(), "ORD-1", null, null).assertSuccess(200).json();

        assertAll(() -> assertEquals(List.of(Long.toString(draft), Long.toString(approved), Long.toString(third)), ids),
            () -> assertEquals(Long.toString(approved), first.path("nextFromFactId").asString()),
            () -> assertEquals(BillingFactStatus.DRAFT.getCode(), first.path("items").get(0).path("status").asString()),
            () -> assertEquals(1, first.path("items").get(0).path("lines").size()),
            () -> assertEquals(ticket, first.path("items").get(1).path("refs").get(0).path("refId").asString()),
            () -> assertEquals(2, order.path("items").size()),
            () -> assertEquals(Long.toString(PAYMENT_FACT_ID), order.path("items").get(0).path("factId").asString()),
            () -> assertEquals(1, order.path("items").get(1).path("links").size()),
            () -> assertTrue(absent(order.path("nextFromFactId"))));
    }

    /** 按引用：无匹配返回空列表与空游标；游标之后没有事实同样为空。 */
    @Test
    void test_listByRefNoMatch() {
        long factId = submitDraft(BillingFactRequestBuilder.template().clearRefs().ref(FactRefType.TICKET, "OPS-ONLY"));

        JsonNode none = byRef(FactRefType.TICKET.getCode(), "OPS-NONE", null, null).assertSuccess(200).json();
        JsonNode after = byRef(FactRefType.TICKET.getCode(), "OPS-ONLY", Long.toString(factId), null).assertSuccess(200)
            .json();

        assertAll(() -> assertEquals(0, none.path("items").size()), () -> assertTrue(absent(none.path("nextFromFactId"))),
            () -> assertEquals(0, after.path("items").size()));
    }

    /** 按引用：引用类型不在目录或格式非法、limit 超出 1–200、游标非法都是参数错误。 */
    @Test
    void test_listByRefInvalidParameter() {
        byRef("UNKNOWN_REF", "1", null, null).assertInvalidRequest();
        byRef("ticket", "1", null, null).assertInvalidRequest();
        byRef(FactRefType.TICKET.getCode(), "1", null, "201").assertInvalidRequest();
        byRef(FactRefType.TICKET.getCode(), "1", "-1", null).assertInvalidRequest();
        byRef(FactRefType.TICKET.getCode(), "1", null, "200").assertSuccess(200);
    }

    /** 逐页读取直到游标为空，返回全部 factId。 */
    List<String> collectPages(Function<String, BillingResponseChecker> pageOf) {
        List<String> ids = new ArrayList<>();
        String cursor = null;
        do {
            BillingResponseChecker page = pageOf.apply(cursor).assertSuccess(200);
            page.json().path("items").forEach(item -> ids.add(item.path("factId").asString()));
            cursor = page.text("nextFromFactId");
        } while (cursor != null);
        return ids;
    }

    /**
     * 前置数据：一条 AUTO PAYMENT（引用 ORDER 与带外部交易号的 CHANNEL_TRANSACTION_REF，渠道成本规则行带 JSON 证据）与一条以其为来源的 REFUND（同一 ORDER，冲回行链接到原行）
     * <li>后续自动事实切片才会产生这些事实；本期只验证读取路径。</li>
     */
    void insertPaymentWithRefundLink() {
        insertFact(PAYMENT_FACT_ID, BillingFactType.PAYMENT, BillingFactStatus.APPROVED, Instant.now());
        insertFact(REFUND_FACT_ID, BillingFactType.REFUND, BillingFactStatus.APPROVED, Instant.now());
        testMapper.insertBillingFactRef(2_100_101L, PAYMENT_FACT_ID, FactRefType.ORDER.getCode(), "ORD-1");
        testMapper.insertBillingFactRef(2_100_103L, PAYMENT_FACT_ID, FactRefType.CHANNEL_TRANSACTION_REF.getCode(),
            CHANNEL_TRANSACTION_REF_ID, EXTERNAL_REFERENCE_TYPE, EXTERNAL_TRANSACTION_ID);
        testMapper.insertBillingFactRef(2_100_102L, REFUND_FACT_ID, FactRefType.ORDER.getCode(), "ORD-1");
        testMapper.insertRuleLine(2_100_011L, PAYMENT_FACT_ID, 1, FactComponentType.CHANNEL_COST.getCode(),
            FactPartyRole.OPERATING_ENTITY.getCode(), ENTITY_NO, FactPartyRole.CHANNEL.getCode(), CHANNEL_ID,
            new BigDecimal("100"), new BigDecimal("0.029"), new BigDecimal("2.9"), new BigDecimal("2.90"),
            FactLineDirection.CHARGE.getCode(), SourceRuleType.CHANNEL_COST.getCode(), 8001L, 8002L,
            "{\"schemaVersion\":\"1\",\"componentCode\":\"PROCESSING\",\"rate\":\"0.029\"}");
        testMapper.insertRuleLine(2_100_021L, REFUND_FACT_ID, 1, FactComponentType.CHANNEL_COST.getCode(),
            FactPartyRole.CHANNEL.getCode(), CHANNEL_ID, FactPartyRole.OPERATING_ENTITY.getCode(), ENTITY_NO,
            new BigDecimal("2.90"), null, new BigDecimal("2.90"), new BigDecimal("2.90"),
            FactLineDirection.REVERSAL.getCode(), null, null, null, null);
        testMapper.insertLineLink(2_100_031L, 2_100_011L, 2_100_021L, LineLinkType.REVERSAL.getCode(),
            new BigDecimal("2.9"));
        testMapper.updateParentFact(REFUND_FACT_ID, PAYMENT_FACT_ID);
    }

    /** 插入一条 OE 账簿下的前置事实。 */
    void insertFact(long factId, BillingFactType factType, BillingFactStatus status, Instant publishedAt) {
        testMapper.insertBillingFact(factId, factType.getCode(), FactOriginType.AUTO.getCode(), "SRC-" + factId,
            Long.valueOf(CLIENT_ID), ENTITY_NO, null, null, CURRENCY, status.getCode(), 1, publishedAt);
    }

    /** 按账簿主体读取一页。 */
    BillingResponseChecker list(String entityType, String entityId, String fromFactId, String limit) {
        return get(PATH + "?entityType=" + entityType + "&entityId=" + entityId + "&currency=" + CURRENCY
            + query(fromFactId, limit));
    }

    /** 按引用读取一页。 */
    BillingResponseChecker byRef(String refType, String refId, String fromFactId, String limit) {
        return get(PATH + "/by-ref?refType=" + refType + "&refId=" + refId + query(fromFactId, limit));
    }

    /** 发布进度。 */
    BillingResponseChecker progress(String entityType, String entityId) {
        return get(PATH + "/progress?entityType=" + entityType + "&entityId=" + entityId + "&currency=" + CURRENCY);
    }

    /** JSON 字段为 null 或未输出。 */
    static boolean absent(JsonNode node) {
        return node.isNull() || node.isMissingNode();
    }

    /** 可选游标与条数查询参数。 */
    static String query(String fromFactId, String limit) {
        StringBuilder query = new StringBuilder();
        if (fromFactId != null) {
            query.append("&fromFactId=").append(fromFactId);
        }
        if (limit != null) {
            query.append("&limit=").append(limit);
        }
        return query.toString();
    }
}
