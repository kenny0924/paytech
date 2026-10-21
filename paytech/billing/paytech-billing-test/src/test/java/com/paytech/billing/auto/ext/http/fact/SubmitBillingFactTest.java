package com.paytech.billing.auto.ext.http.fact;

import static com.paytech.billing.test.builder.BillingFactRequestBuilder.CHANNEL_ID;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.CLEARING_ACCOUNT_ID;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.CLIENT_ID;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.CURRENCY;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.ENTITY_NO;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.FINANCE_ENTITY_ID;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.LOCAL_PARTNER_ID;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.MAKER_ID;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.OTHER_CLIENT_ID;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.OTHER_ENTITY_NO;
import static com.paytech.billing.test.builder.CurrencySpecResponseBuilder.EUR;
import static com.paytech.billing.test.builder.CurrencySpecResponseBuilder.GBP;
import static com.paytech.billing.test.builder.CurrencySpecResponseBuilder.JPY;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.billing.api.BillingFactCommandApi;
import com.paytech.billing.api.enums.BillingFactErrorCode;
import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactOriginType;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.core.enums.FactTargetBucket;
import com.paytech.billing.test.base.AbstractBillingFactApiTest;
import com.paytech.billing.test.builder.BillingFactRequestBuilder;
import com.paytech.billing.test.builder.CurrencySpecResponseBuilder;
import com.paytech.billing.test.checker.BillingResponseChecker;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

import tools.jackson.databind.JsonNode;

/**
 * 提交计费事实（DRAFT）：从真实 HTTP 入口经 Processor、Service、Repository 写入本地 mor_billing
 * <li>覆盖新提交（头、TICKET 引用、明细行与证据字段）、同来源引用同内容重放与改内容拒绝、两类人工登记的目录约束、合作方二元组与平台服务费
 * 主体不变量、目标余额桶、币种启用与精度、目录外代码，以及成本调整的来源支付事实与 CLEARING_ACCOUNT 要求。提交不登记发布任务。</li>
 * <li>币种规则来自 Platform 币种快照（Platform Mock + 显式刷新）：启用过（ACTIVE、INACTIVE）可登记，DRAFT 与未知币种拒绝；Platform 新启用的
 * 币种经一次刷新后被识别，登记路径不回源。</li>
 *
 * @see BillingFactCommandApi#submitBillingFact(String, String, com.paytech.billing.api.request.SubmitBillingFactRequestBody)
 */
class SubmitBillingFactTest extends AbstractBillingFactApiTest {

    /** 新提交：201 DRAFT、版本 0、MANUAL、业务时间为提交时间；引用、明细行与证据字段返回；详情一致，未登记发布任务。 */
    @Test
    void test_normalFlow() {
        BillingFactRequestBuilder builder = BillingFactRequestBuilder.template()
            .addLine(FactComponentType.CLIENT_FEE, FactPartyRole.CLIENT, CLIENT_ID, FactPartyRole.OPERATING_ENTITY,
                ENTITY_NO, "1.5", FactLineDirection.CHARGE);

        BillingResponseChecker response = submit(builder).assertSuccess(201)
            .assertText("factType", BillingFactType.MANUAL_ADJUSTMENT.getCode())
            .assertText("originType", FactOriginType.MANUAL.getCode())
            .assertText("status", BillingFactStatus.DRAFT.getCode()).assertText("factVersion", "0")
            .assertText("sourceEventKey", builder.sourceReference()).assertText("sourceVersion", "1")
            .assertText("grossAmount", "11.50").assertText("makerId", MAKER_ID).assertText("checkerId", null)
            .assertText("recordedTime", null).assertText("approvedTime", null).assertText("publishedAt", null)
            .assertText("pricing", null);
        long factId = response.id("factId");
        JsonNode body = response.json();
        JsonNode lines = body.path("lines");

        assertAll(() -> assertNotNull(response.text("businessTime")),
            () -> assertTrue(response.text("sourcePayloadHash").matches("[0-9a-f]{64}")),
            () -> assertEquals(1, body.path("refs").size()),
            () -> assertEquals(FactRefType.TICKET.getCode(), body.path("refs").get(0).path("refType").asString()),
            () -> assertEquals(0, body.path("links").size()), () -> assertEquals(2, lines.size()),
            () -> assertEquals("1", lines.get(0).path("lineNo").asString()),
            () -> assertEquals("10.00", lines.get(0).path("postedAmount").asString()),
            () -> assertEquals("10.00000000", lines.get(0).path("basisAmount").asString()),
            () -> assertEquals("10.00000000", lines.get(0).path("rawAmount").asString()),
            () -> assertTrue(absent(lines.get(0).path("rateValue"))),
            () -> assertTrue(absent(lines.get(0).path("sourceRuleType"))),
            () -> assertTrue(absent(lines.get(0).path("sourceEvidence"))),
            () -> assertTrue(absent(lines.get(0).path("targetBucket"))),
            () -> assertEquals(FactPartyRole.OPERATING_ENTITY.getCode(), lines.get(0).path("payerRole").asString()),
            () -> assertEquals(CLIENT_ID, lines.get(0).path("payeeId").asString()),
            () -> assertEquals("1.50", lines.get(1).path("postedAmount").asString()),
            () -> assertEquals(FactComponentType.CLIENT_FEE.getCode(), lines.get(1).path("componentType").asString()));
        assertEquals(body, getFact(factId).assertSuccess(200).json());
        Map<String, Object> row = factRow(factId);
        assertAll(() -> assertEquals(FactOriginType.MANUAL.getCode(), row.get("origin_type")),
            () -> assertNotNull(row.get("business_time")), () -> assertNull(row.get("approved_time")),
            () -> assertEquals(2, testMapper.selectBillingFactLines(factId).size()),
            () -> assertEquals(1, testMapper.selectBillingFactRefs(factId).size()));
        events.assertNoEnvelope(factId);
    }

    /** 同来源引用同内容重复提交返回原事实（200）；改金额或改引用都按内容不同拒绝，均不新增事实。 */
    @Test
    void test_replayBySourceReference() {
        BillingFactRequestBuilder builder = BillingFactRequestBuilder.template();
        long factId = submitDraft(builder);

        submit(builder).assertSuccess(200).assertText("factId", Long.toString(factId))
            .assertText("status", BillingFactStatus.DRAFT.getCode());
        submit(BillingFactRequestBuilder.template().sourceReference(builder.sourceReference()).clearLines()
            .addLine(FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT,
                CLIENT_ID, "10.01", FactLineDirection.CHARGE))
            .assertBusinessRejected("来源引用已登记且内容不同");
        submit(BillingFactRequestBuilder.template().sourceReference(builder.sourceReference()).clearRefs()
            .ref(FactRefType.TICKET, "OPS-OTHER")).assertBusinessRejected("来源引用已登记且内容不同");

        assertEquals(1, testMapper.selectBillingFactLines(factId).size());
        assertEquals(1, testMapper.selectBillingFactRefs(factId).size());
    }

    /** 不带引用的人工调整同样合法，引用为空列表。 */
    @Test
    void test_manualAdjustmentWithoutRefs() {
        long factId = submit(BillingFactRequestBuilder.template().clearRefs()).assertSuccess(201).id("factId");

        assertEquals(0, getFact(factId).assertSuccess(200).json().path("refs").size());
        assertEquals(List.of(), testMapper.selectBillingFactRefs(factId));
    }

    /** 合作方二元组必须同有同无；缺二元组时不能登记合作方分润。 */
    @Test
    void test_partnerPairInvariant() {
        submit(BillingFactRequestBuilder.template().partner(LOCAL_PARTNER_ID, null))
            .assertBusinessRejected("合作方二元组不完整");
        submit(BillingFactRequestBuilder.template().partner(null, FINANCE_ENTITY_ID))
            .assertBusinessRejected("合作方二元组不完整");
        submit(BillingFactRequestBuilder.template().clearLines().addLine(FactComponentType.PARTNER_SHARE,
            FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.LOCAL_PARTNER, LOCAL_PARTNER_ID, "3.00",
            FactLineDirection.CHARGE)).assertFailCode(BillingFactErrorCode.FACT_LINE_INVALID);
    }

    /** 平台服务费收方必须是头部 Finance Entity；完整二元组且收方一致时登记成功。 */
    @Test
    void test_serviceChargeSubjectInvariant() {
        submit(BillingFactRequestBuilder.template().partner().clearLines().addLine(
            FactComponentType.PLATFORM_SERVICE_CHARGE, FactPartyRole.LOCAL_PARTNER, LOCAL_PARTNER_ID, FactPartyRole.CLIENT,
            CLIENT_ID, "2.00", FactLineDirection.CHARGE)).assertFailCode(BillingFactErrorCode.FACT_LINE_INVALID);
        submit(BillingFactRequestBuilder.template().partner().clearLines().addLine(
            FactComponentType.PLATFORM_SERVICE_CHARGE, FactPartyRole.LOCAL_PARTNER, LOCAL_PARTNER_ID,
            FactPartyRole.FINANCE_ENTITY, "529999", "2.00", FactLineDirection.CHARGE))
            .assertFailCode(BillingFactErrorCode.FACT_LINE_INVALID);

        submit(BillingFactRequestBuilder.template().partner().clearLines().addLine(
            FactComponentType.PLATFORM_SERVICE_CHARGE, FactPartyRole.LOCAL_PARTNER, LOCAL_PARTNER_ID,
            FactPartyRole.FINANCE_ENTITY, FINANCE_ENTITY_ID, "2.00", FactLineDirection.CHARGE))
            .assertSuccess(201).assertText("financeEntityId", FINANCE_ENTITY_ID)
            .assertText("localPartnerId", LOCAL_PARTNER_ID);
    }

    /** 人工调整目录：必须有 Client、不得有来源事实、组件限六类。 */
    @Test
    void test_manualAdjustmentCatalog() {
        submit(BillingFactRequestBuilder.template().client(null)).assertBusinessRejected("人工调整必须指定 Client");
        submit(BillingFactRequestBuilder.template().parentFact(2_000_001L)).assertBusinessRejected("人工调整不得指定来源事实");
        submit(BillingFactRequestBuilder.template().clearLines().addLine(FactComponentType.PAYOUT_FEE,
            FactPartyRole.CLIENT, CLIENT_ID, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, "1.00", FactLineDirection.CHARGE))
            .assertFailCode(BillingFactErrorCode.FACT_LINE_INVALID);
        submit(BillingFactRequestBuilder.template().clearLines().addLine(FactComponentType.SELLER_TAX,
            FactPartyRole.CLIENT, CLIENT_ID, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, "1.00", FactLineDirection.REVERSAL))
            .assertSuccess(201);
    }

    /** 人工调整只允许 TICKET 引用：CLEARING_ACCOUNT 与目录外引用类型、重复引用类型都是参数错误。 */
    @Test
    void test_manualAdjustmentRefTypes() {
        submit(BillingFactRequestBuilder.template().ref(FactRefType.CLEARING_ACCOUNT, CLEARING_ACCOUNT_ID))
            .assertInvalidRequest().assertDetailContains(FactRefType.CLEARING_ACCOUNT.getCode());
        submit(BillingFactRequestBuilder.template().rawRef("UNKNOWN_REF", "1")).assertInvalidRequest();
        submit(BillingFactRequestBuilder.template().ref(FactRefType.TICKET, "OPS-2")).assertInvalidRequest()
            .assertDetailContains("同一引用类型至多一条");
    }

    /** 目标余额桶：收方为 CLIENT / LOCAL_PARTNER 时可设置并落库、进入响应；收方为 OE 时拒绝。 */
    @Test
    void test_targetBucket() {
        long factId = submit(BillingFactRequestBuilder.template().partner().clearLines()
            .addLine(FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT,
                CLIENT_ID, "5.00", FactLineDirection.ADJUSTMENT, FactTargetBucket.AVAILABLE)
            .addLine(FactComponentType.PARTNER_SHARE, FactPartyRole.OPERATING_ENTITY, ENTITY_NO,
                FactPartyRole.LOCAL_PARTNER, LOCAL_PARTNER_ID, "1.00", FactLineDirection.ADJUSTMENT, FactTargetBucket.PENDING))
            .assertSuccess(201).id("factId");

        JsonNode lines = getFact(factId).assertSuccess(200).json().path("lines");
        List<Map<String, Object>> rows = testMapper.selectBillingFactLines(factId);
        assertAll(() -> assertEquals(FactTargetBucket.AVAILABLE.getCode(), lines.get(0).path("targetBucket").asString()),
            () -> assertEquals(FactTargetBucket.PENDING.getCode(), lines.get(1).path("targetBucket").asString()),
            () -> assertEquals(FactTargetBucket.AVAILABLE.getCode(), rows.get(0).get("target_bucket")),
            () -> assertEquals(FactTargetBucket.PENDING.getCode(), rows.get(1).get("target_bucket")));
        submit(BillingFactRequestBuilder.template().clearLines().addLine(FactComponentType.CLIENT_FEE,
            FactPartyRole.CLIENT, CLIENT_ID, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, "1.00", FactLineDirection.CHARGE,
            FactTargetBucket.AVAILABLE)).assertFailCode(BillingFactErrorCode.FACT_LINE_INVALID);
        submit(BillingFactRequestBuilder.template().clearLines().addRawLine(FactComponentType.CLIENT_PRINCIPAL.getCode(),
            FactPartyRole.OPERATING_ENTITY.getCode(), ENTITY_NO, FactPartyRole.CLIENT.getCode(), CLIENT_ID, "1.00",
            FactLineDirection.CHARGE.getCode(), "FROZEN")).assertValidationError();
    }

    /** 币种必须在 Platform 币种快照中，金额精度不得超过币种 minor unit（不隐式舍入）。 */
    @Test
    void test_currencyPrecision() {
        submit(BillingFactRequestBuilder.template().currency(EUR)).assertBusinessRejected("币种不存在或尚未同步: " + EUR);
        submit(BillingFactRequestBuilder.template().clearLines().addLine(FactComponentType.CLIENT_PRINCIPAL,
            FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT, CLIENT_ID, "10.001",
            FactLineDirection.CHARGE)).assertFailCode(BillingFactErrorCode.FACT_LINE_INVALID);
    }

    /** 目录外组件与角色在 Processor 以参数错误（400）拒绝；违反 Body 正则或必填的字段由入口校验以 422 拒绝；缺少或畸形的
     * 操作人 Header 以参数错误（400）拒绝；均不保存。 */
    @Test
    void test_invalidParameter() {
        submit(BillingFactRequestBuilder.template().addRawLine("UNKNOWN_FEE", FactPartyRole.CLIENT.getCode(), CLIENT_ID,
            FactPartyRole.OPERATING_ENTITY.getCode(), ENTITY_NO, "1.00", FactLineDirection.CHARGE.getCode(), null))
            .assertInvalidRequest();
        submit(BillingFactRequestBuilder.template().addRawLine(FactComponentType.CLIENT_FEE.getCode(), "MERCHANT",
            CLIENT_ID, FactPartyRole.OPERATING_ENTITY.getCode(), ENTITY_NO, "1.00", FactLineDirection.CHARGE.getCode(),
            null)).assertInvalidRequest();
        submit(BillingFactRequestBuilder.template().addRawLine(FactComponentType.CLIENT_FEE.getCode(),
            FactPartyRole.CLIENT.getCode(), CLIENT_ID, FactPartyRole.OPERATING_ENTITY.getCode(), ENTITY_NO, "1.00",
            "REFUND", null)).assertValidationError();
        submit(BillingFactRequestBuilder.template().factType(BillingFactType.PAYMENT)).assertValidationError();
        submit(BillingFactRequestBuilder.template().maker(null)).assertInvalidRequest();
        submit(BillingFactRequestBuilder.template().maker("0")).assertInvalidRequest();
        submit(BillingFactRequestBuilder.template().clearLines()).assertValidationError();
    }

    /** 停用（INACTIVE）只拦新用途：启用过的币种仍可登记、批准并发布计费事实（Q2、Q7）。 */
    @Test
    void test_inactiveCurrencyAccepted() {
        currencySpecs.mockCurrencySpecs(CurrencySpecResponseBuilder.template().build(),
            CurrencySpecResponseBuilder.template().currency(EUR).status(CurrencySpecStatus.INACTIVE).build());
        assertTrue(currencySnapshotRefresher.refresh());

        long factId = submit(BillingFactRequestBuilder.template().currency(EUR)).assertSuccess(201)
            .assertText("currency", EUR).assertText("grossAmount", "10.00").id("factId");
        approve(factId, CHECKER_ID).assertSuccess(200);

        events.awaitEnvelope(factId).envelope(factId, 1, instanceId);
        assertNotNull(factRow(factId).get("published_at"));
    }

    /** DRAFT 从未生效、未知币种不在快照：均业务拒绝且不保存；只有未知币种请求刷新快照。 */
    @Test
    void test_draftAndUnknownCurrencyRejected() {
        currencySpecs.mockCurrencySpecs(CurrencySpecResponseBuilder.template().build(),
            CurrencySpecResponseBuilder.template().currency(GBP).status(CurrencySpecStatus.DRAFT).build());
        assertTrue(currencySnapshotRefresher.refresh());

        submit(BillingFactRequestBuilder.template().currency(GBP))
            .assertBusinessRejected("币种未启用: " + GBP + ", 状态 " + CurrencySpecStatus.DRAFT.getCode());
        assertFalse(currencySnapshot.refreshRequested());
        submit(BillingFactRequestBuilder.template().currency(EUR)).assertBusinessRejected("币种不存在或尚未同步: " + EUR);

        assertAll(() -> assertTrue(currencySnapshot.refreshRequested()), () -> assertEquals(0L, factCount()));
    }

    /**
     * Platform 新启用的币种：本实例快照未包含时拒绝并请求刷新，登记路径不回源 Platform；下一次检查刷新后按 Platform 精度登记
     * （AC-05、AC-08）。
     */
    @Test
    void test_newlyActiveCurrencyRecognizedAfterRefresh() {
        currencySpecs.mockCurrencySpecs(CurrencySpecResponseBuilder.template().build(),
            CurrencySpecResponseBuilder.template().currency(JPY).minorUnit(0).build());
        int platformCalls = currencySpecs.listCalls();

        submit(BillingFactRequestBuilder.template().currency(JPY)).assertBusinessRejected("币种不存在或尚未同步: " + JPY);
        assertAll(() -> assertTrue(currencySnapshot.refreshRequested()),
            () -> assertEquals(platformCalls, currencySpecs.listCalls(), "登记路径不得回源 Platform"));

        currencySnapshotRefresher.refreshIfDue();

        assertAll(() -> assertEquals(platformCalls + 1, currencySpecs.listCalls()),
            () -> assertFalse(currencySnapshot.refreshRequested()));
        submit(BillingFactRequestBuilder.template().currency(JPY)).assertSuccess(201).assertText("currency", JPY)
            .assertText("grossAmount", "10");
        submit(BillingFactRequestBuilder.template().currency(JPY).clearLines().addLine(FactComponentType.CLIENT_PRINCIPAL,
            FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT, CLIENT_ID, "10.5", FactLineDirection.CHARGE))
            .assertFailCode(BillingFactErrorCode.FACT_LINE_INVALID);
    }

    /** 零金额与付方等于收方是业务拒绝。 */
    @Test
    void test_lineAmountAndParties() {
        submit(BillingFactRequestBuilder.template().clearLines().addLine(FactComponentType.CLIENT_PRINCIPAL,
            FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT, CLIENT_ID, "0.00", FactLineDirection.CHARGE))
            .assertFailCode(BillingFactErrorCode.FACT_LINE_INVALID);
        submit(BillingFactRequestBuilder.template().clearLines().addLine(FactComponentType.CLIENT_PRINCIPAL,
            FactPartyRole.CLIENT, CLIENT_ID, FactPartyRole.CLIENT, CLIENT_ID, "1.00", FactLineDirection.CHARGE))
            .assertFailCode(BillingFactErrorCode.FACT_LINE_INVALID);
        submit(BillingFactRequestBuilder.template().clearLines().addLine(FactComponentType.CLIENT_PRINCIPAL,
            FactPartyRole.OPERATING_ENTITY, OTHER_ENTITY_NO, FactPartyRole.CLIENT, CLIENT_ID, "1.00",
            FactLineDirection.CHARGE)).assertFailCode(BillingFactErrorCode.FACT_LINE_INVALID);
    }

    /** 成本调整：来源支付事实与 CLEARING_ACCOUNT 必填，只登记 CHANNEL_COST + ADJUSTMENT，付方 / 收方为渠道与 OE；Client 取自来源事实。 */
    @Test
    void test_costAdjustment() {
        long paymentFactId = insertApprovedPayment(2_000_001L, ENTITY_NO);

        long factId = submit(BillingFactRequestBuilder.costAdjustment(paymentFactId)).assertSuccess(201)
            .assertText("factType", BillingFactType.COST_ADJUSTMENT.getCode())
            .assertText("parentFactId", Long.toString(paymentFactId)).assertText("clientId", CLIENT_ID).id("factId");

        JsonNode body = getFact(factId).assertSuccess(200).json();
        assertAll(() -> assertEquals(FactRefType.CLEARING_ACCOUNT.getCode(), body.path("refs").get(0).path("refType").asString()),
            () -> assertEquals(CLEARING_ACCOUNT_ID, body.path("refs").get(0).path("refId").asString()),
            () -> assertEquals(Long.valueOf(CLIENT_ID), ((Number)factRow(factId).get("client_id")).longValue()),
            () -> assertNull(factRow(paymentFactId).get("published_at")));
    }

    /** 成本调整缺少来源事实或 CLEARING_ACCOUNT、来源事实不合格、Client 与来源事实不一致时拒绝。 */
    @Test
    void test_costAdjustmentAnchors() {
        long paymentFactId = insertApprovedPayment(2_000_002L, ENTITY_NO);
        long otherOePayment = insertApprovedPayment(2_000_003L, OTHER_ENTITY_NO);
        BillingFactRequestBuilder noParent = BillingFactRequestBuilder.costAdjustment(paymentFactId);
        noParent.build().setParentFactId(null);

        submit(noParent).assertBusinessRejected("成本调整必须指定来源支付事实");
        submit(BillingFactRequestBuilder.costAdjustment(paymentFactId).clearRefs())
            .assertBusinessRejected("成本调整必须引用 CLEARING_ACCOUNT");
        submit(BillingFactRequestBuilder.costAdjustment(otherOePayment))
            .assertBusinessRejected("来源事实不存在、不是已批准的同主体同币种支付事实");
        submit(BillingFactRequestBuilder.costAdjustment(9_999_999L))
            .assertBusinessRejected("来源事实不存在、不是已批准的同主体同币种支付事实");
        submit(BillingFactRequestBuilder.costAdjustment(paymentFactId).client(OTHER_CLIENT_ID))
            .assertBusinessRejected("来源事实不存在、不是已批准的同主体同币种支付事实");
        submit(BillingFactRequestBuilder.costAdjustment(paymentFactId).client(CLIENT_ID)).assertSuccess(201);
    }

    /** 成本调整行目录：非 CHANNEL_COST、非 ADJUSTMENT、非渠道 ↔ OE 或给出目标余额桶时拒绝。 */
    @Test
    void test_costAdjustmentLineCatalog() {
        long paymentFactId = insertApprovedPayment(2_000_004L, ENTITY_NO);

        submit(BillingFactRequestBuilder.costAdjustment(paymentFactId).clearLines().addLine(FactComponentType.CLIENT_FEE,
            FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CHANNEL, CHANNEL_ID, "1.00",
            FactLineDirection.ADJUSTMENT)).assertFailCode(BillingFactErrorCode.FACT_LINE_INVALID);
        submit(BillingFactRequestBuilder.costAdjustment(paymentFactId).clearLines().addLine(FactComponentType.CHANNEL_COST,
            FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CHANNEL, CHANNEL_ID, "1.00", FactLineDirection.CHARGE))
            .assertFailCode(BillingFactErrorCode.FACT_LINE_INVALID);
        submit(BillingFactRequestBuilder.costAdjustment(paymentFactId).clearLines().addLine(FactComponentType.CHANNEL_COST,
            FactPartyRole.CLIENT, CLIENT_ID, FactPartyRole.CHANNEL, CHANNEL_ID, "1.00", FactLineDirection.ADJUSTMENT))
            .assertFailCode(BillingFactErrorCode.FACT_LINE_INVALID);
        submit(BillingFactRequestBuilder.costAdjustment(paymentFactId).clearLines().addLine(FactComponentType.CHANNEL_COST,
            FactPartyRole.CHANNEL, CHANNEL_ID, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, "1.00",
            FactLineDirection.ADJUSTMENT, FactTargetBucket.AVAILABLE)).assertFailCode(BillingFactErrorCode.FACT_LINE_INVALID);
        submit(BillingFactRequestBuilder.costAdjustment(paymentFactId).clearLines().addLine(FactComponentType.CHANNEL_COST,
            FactPartyRole.CHANNEL, CHANNEL_ID, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, "0.25",
            FactLineDirection.ADJUSTMENT)).assertSuccess(201);
    }

    /** 计费事实头行数。 */
    long factCount() {
        return testMapper.countWritableRows().stream().filter(row -> "t_billing_fact".equals(row.get("t")))
            .mapToLong(row -> ((Number)row.get("c")).longValue()).sum();
    }

    /** JSON 字段为 null 或未输出。 */
    static boolean absent(JsonNode node) {
        return node.isNull() || node.isMissingNode();
    }

    /** 插入一条已批准的 AUTO PAYMENT 前置事实（后续自动事实切片才会生成）。 */
    long insertApprovedPayment(long factId, String entityNo) {
        testMapper.insertBillingFact(factId, BillingFactType.PAYMENT.getCode(), FactOriginType.AUTO.getCode(),
            "PAY-" + factId, Long.valueOf(CLIENT_ID), entityNo, null, null, CURRENCY,
            BillingFactStatus.APPROVED.getCode(), 1, null);
        return factId;
    }
}
