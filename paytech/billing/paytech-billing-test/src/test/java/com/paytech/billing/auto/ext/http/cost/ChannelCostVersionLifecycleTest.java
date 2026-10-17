package com.paytech.billing.auto.ext.http.cost;

import static com.paytech.billing.test.builder.ChannelCostVersionRequestBuilder.component;
import static com.paytech.billing.test.builder.ChannelCostVersionRequestBuilder.scope;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.billing.core.enums.AmountRoundingMode;
import com.paytech.billing.core.enums.ChannelCostCalculationBase;
import com.paytech.billing.core.enums.ChannelCostComponentType;
import com.paytech.billing.core.enums.ChannelCostPlanStatus;
import com.paytech.billing.core.enums.ChannelCostVersionStatus;
import com.paytech.billing.test.base.AbstractChannelCostApiTest;
import com.paytech.billing.test.builder.ChannelCostVersionRequestBuilder;

import tools.jackson.databind.JsonNode;

/**
 * 渠道成本版本生命周期：创建 DRAFT 与内容摘要、提交、Maker 自审与摘要不一致被拒、到期批准切换当前版本、旧版本 INACTIVE、
 * 未来版本仅 APPROVED、驳回与已驳回不可批准、待处理版本阻塞新建及查询排序。
 *
 * @see com.paytech.billing.api.ChannelCostPlanApi#createChannelCostVersion(String, com.paytech.billing.api.request.CreateChannelCostVersionRequestBody)
 * @see com.paytech.billing.api.ChannelCostPlanApi#submitChannelCostVersion(String, String, com.paytech.billing.api.request.SubmitChannelCostVersionRequestBody)
 * @see com.paytech.billing.api.ChannelCostPlanApi#approveChannelCostVersion(String, String, com.paytech.billing.api.request.ReviewChannelCostVersionRequestBody)
 * @see com.paytech.billing.api.ChannelCostPlanApi#rejectChannelCostVersion(String, String, com.paytech.billing.api.request.ReviewChannelCostVersionRequestBody)
 * @see com.paytech.billing.api.ChannelCostPlanApi#getChannelCostPlan(String)
 * @see com.paytech.billing.api.ChannelCostPlanApi#getChannelCostVersion(String, String)
 */
class ChannelCostVersionLifecycleTest extends AbstractChannelCostApiTest {
    /** 内容摘要格式：64 位小写十六进制。 */
    private static final String HASH_PATTERN = "[0-9a-f]{64}";

    /** 与任何版本都不同的摘要。 */
    private static final String WRONG_HASH = "0".repeat(64);

    /** 本类公共配置：标准 DRAFT 方案。 */
    @BeforeEach
    void setUpClassMocks() {
        createStandardPlan();
    }

    /** 创建 DRAFT：版本号 1、摘要为 64 位小写十六进制且查询时不变，范围与组成逐值回显，方案指针不变。 */
    @Test
    void test_createDraftEchoesScopes() {
        Instant effectiveFrom = minutesAgo(1);
        var created = createVersion(ChannelCostVersionRequestBuilder.template(MAKER_ID, effectiveFrom))
            .assertSuccess(201).assertText("planCode", PLAN_CODE).assertText("costVer", "1")
            .assertText("status", ChannelCostVersionStatus.DRAFT.getCode()).assertText("makerId", MAKER_ID);
        String hash = created.text("contentHash");

        assertAll(() -> assertEquals(effectiveFrom, Instant.parse(created.text("effectiveFrom"))),
            () -> assertNull(created.text("effectiveTo")), () -> assertNull(created.text("checkerId")),
            () -> assertTrue(hash != null && hash.matches(HASH_PATTERN), "contentHash: " + hash));
        assertStandardScope(created.json().path("scopes"));

        var queried = getVersion(1).assertSuccess(200).assertText("contentHash", hash);
        assertStandardScope(queried.json().path("scopes"));
        JsonNode versions = getPlan(PLAN_CODE).assertSuccess(200).json().path("versions");
        assertEquals(1, versions.size());
        assertSummary(versions.get(0), 1, ChannelCostVersionStatus.DRAFT, null);
        assertEquals(hash, versions.get(0).path("contentHash").asString());
        assertPlanRow(PLAN_CODE, ChannelCostPlanStatus.DRAFT.getCode(), null, 0L);
    }

    /** 版本详情范围按 scopeNo 升序、范围内组成按 componentCode 升序，与提交顺序无关。 */
    @Test
    void test_queryOrdersScopesAndComponents() {
        ChannelCostVersionRequestBuilder builder = version().clearScopes().addScope(
            scope(2, component("Z_FEE", ChannelCostComponentType.REFUND_FEE, null, "1.00")),
            scope(1, component("B_FEE", ChannelCostComponentType.PROCESSING_FEE, "0.02", null),
                component("A_FEE", ChannelCostComponentType.CROSS_BORDER_FEE, "0.01", null)));
        builder.scope(0).setActionType("REFUND");
        createVersion(builder).assertSuccess(201);

        JsonNode scopes = getVersion(1).assertSuccess(200).json().path("scopes");

        assertEquals(2, scopes.size());
        assertEquals(1, scopes.get(0).path("scopeNo").asInt());
        assertEquals(2, scopes.get(1).path("scopeNo").asInt());
        assertEquals("REFUND", scopes.get(1).path("actionType").asString());
        JsonNode first = scopes.get(0).path("components");
        assertEquals(2, first.size());
        assertEquals("A_FEE", first.get(0).path("componentCode").asString());
        assertEquals("B_FEE", first.get(1).path("componentCode").asString());
        assertEquals("Z_FEE", scopes.get(1).path("components").get(0).path("componentCode").asString());
    }

    /** 提交冻结为 PENDING_APPROVAL；非编制人提交、重复提交均被拒。 */
    @Test
    void test_submitByNonMakerRejected() {
        createVersion(version()).assertSuccess(201);

        submit(1, CHECKER_ID).assertBusinessRejected(CHECKER_ID);
        getVersion(1).assertSuccess(200).assertText("status", ChannelCostVersionStatus.DRAFT.getCode());

        submit(1, MAKER_ID).assertSuccess(200).assertText("status",
            ChannelCostVersionStatus.PENDING_APPROVAL.getCode());
        submit(1, MAKER_ID).assertBusinessRejected(ChannelCostVersionStatus.PENDING_APPROVAL.getCode());
    }

    /** Maker 不能批准或驳回自己的版本，版本保持 PENDING_APPROVAL。 */
    @Test
    void test_makerSelfReviewRejected() {
        String hash = createSubmitted(version()).text("contentHash");

        approve(1, MAKER_ID, hash).assertBusinessRejected(MAKER_ID);
        reject(1, MAKER_ID, hash).assertBusinessRejected(MAKER_ID);

        getVersion(1).assertSuccess(200).assertText("status", ChannelCostVersionStatus.PENDING_APPROVAL.getCode());
        assertPlanRow(PLAN_CODE, ChannelCostPlanStatus.DRAFT.getCode(), null, 0L);
    }

    /** 复核摘要与版本摘要不一致：批准与驳回均被拒。 */
    @Test
    void test_contentHashMismatchRejected() {
        createSubmitted(version());

        approve(1, CHECKER_ID, WRONG_HASH).assertBusinessRejected("复核确认的内容摘要与版本不一致");
        reject(1, CHECKER_ID, WRONG_HASH).assertBusinessRejected("复核确认的内容摘要与版本不一致");

        getVersion(1).assertSuccess(200).assertText("status", ChannelCostVersionStatus.PENDING_APPROVAL.getCode());
        assertPlanRow(PLAN_CODE, ChannelCostPlanStatus.DRAFT.getCode(), null, 0L);
    }

    /** 已到生效时间批准：版本 ACTIVE 并写复核人，方案 DRAFT → ACTIVE、当前版本 1、乐观锁版本 +1。 */
    @Test
    void test_effectiveApprovalActivatesPlan() {
        createApproved(version()).assertText("status", ChannelCostVersionStatus.ACTIVE.getCode())
            .assertText("checkerId", CHECKER_ID);

        var plan = getPlan(PLAN_CODE).assertSuccess(200)
            .assertText("status", ChannelCostPlanStatus.ACTIVE.getCode()).assertText("currentCostVer", "1");
        assertSummary(plan.json().path("versions").get(0), 1, ChannelCostVersionStatus.ACTIVE, null);
        assertPlanRow(PLAN_CODE, ChannelCostPlanStatus.ACTIVE.getCode(), 1, 1L);
    }

    /** 第二版本到期批准：新版本 ACTIVE、原版本 INACTIVE 且失效时间 = 新版本生效时间，摘要按版本号降序。 */
    @Test
    void test_secondVersionRetiresPrevious() {
        createApproved(ChannelCostVersionRequestBuilder.template(MAKER_ID, minutesAgo(10)));
        Instant secondFrom = minutesAgo(5);

        createApproved(ChannelCostVersionRequestBuilder.template(MAKER_ID, secondFrom))
            .assertText("status", ChannelCostVersionStatus.ACTIVE.getCode());

        JsonNode versions = getPlan(PLAN_CODE).assertSuccess(200).assertText("currentCostVer", "2").json()
            .path("versions");
        assertEquals(2, versions.size());
        assertSummary(versions.get(0), 2, ChannelCostVersionStatus.ACTIVE, null);
        assertSummary(versions.get(1), 1, ChannelCostVersionStatus.INACTIVE, secondFrom);
        getVersion(1).assertSuccess(200).assertText("status", ChannelCostVersionStatus.INACTIVE.getCode());
        assertPlanRow(PLAN_CODE, ChannelCostPlanStatus.ACTIVE.getCode(), 2, 2L);
    }

    /** 到期批准的新版本生效时间不晚于当前版本：被拒，版本保持 PENDING_APPROVAL，当前版本不变。 */
    @Test
    void test_earlierEffectiveVersionRejected() {
        createApproved(ChannelCostVersionRequestBuilder.template(MAKER_ID, minutesAgo(5)));
        String hash = createSubmitted(ChannelCostVersionRequestBuilder.template(MAKER_ID, minutesAgo(10)))
            .text("contentHash");

        approve(2, CHECKER_ID, hash).assertBusinessRejected("新版本生效时间必须晚于当前版本");

        getVersion(2).assertSuccess(200).assertText("status", ChannelCostVersionStatus.PENDING_APPROVAL.getCode());
        getVersion(1).assertSuccess(200).assertText("status", ChannelCostVersionStatus.ACTIVE.getCode());
        assertPlanRow(PLAN_CODE, ChannelCostPlanStatus.ACTIVE.getCode(), 1, 1L);
    }

    /** 生效时间在未来：批准后只到 APPROVED，当前版本、旧版本状态与失效时间保持不变。 */
    @Test
    void test_futureEffectiveApprovalStaysApproved() {
        createApproved(version());

        createApproved(ChannelCostVersionRequestBuilder.template(MAKER_ID, tomorrow()))
            .assertText("status", ChannelCostVersionStatus.APPROVED.getCode()).assertText("checkerId", CHECKER_ID);

        getPlan(PLAN_CODE).assertSuccess(200).assertText("currentCostVer", "1");
        getVersion(1).assertSuccess(200).assertText("status", ChannelCostVersionStatus.ACTIVE.getCode())
            .assertText("effectiveTo", null);
        assertPlanRow(PLAN_CODE, ChannelCostPlanStatus.ACTIVE.getCode(), 1, 1L);
    }

    /** 首个版本生效时间在未来：APPROVED，方案仍为 DRAFT 且无当前版本。 */
    @Test
    void test_futureFirstVersionKeepsPlanDraft() {
        createApproved(ChannelCostVersionRequestBuilder.template(MAKER_ID, tomorrow()))
            .assertText("status", ChannelCostVersionStatus.APPROVED.getCode());

        assertNull(getPlan(PLAN_CODE).assertSuccess(200).assertText("status", ChannelCostPlanStatus.DRAFT.getCode())
            .text("currentCostVer"));
        assertPlanRow(PLAN_CODE, ChannelCostPlanStatus.DRAFT.getCode(), null, 0L);
    }

    /** 驳回为 REJECTED 并写复核人；驳回后不再阻塞新建版本。 */
    @Test
    void test_rejectMarksRejected() {
        String hash = createSubmitted(version()).text("contentHash");

        reject(1, CHECKER_ID, hash).assertSuccess(200)
            .assertText("status", ChannelCostVersionStatus.REJECTED.getCode()).assertText("checkerId", CHECKER_ID);
        assertPlanRow(PLAN_CODE, ChannelCostPlanStatus.DRAFT.getCode(), null, 0L);

        createVersion(version()).assertSuccess(201).assertText("costVer", "2");
    }

    /** 已驳回版本不可批准，也不可再次驳回或提交。 */
    @Test
    void test_rejectedCannotBeApproved() {
        String hash = createSubmitted(version()).text("contentHash");
        reject(1, CHECKER_ID, hash).assertSuccess(200);

        approve(1, CHECKER_ID, hash).assertBusinessRejected(ChannelCostVersionStatus.REJECTED.getCode());
        reject(1, CHECKER_ID, hash).assertBusinessRejected(ChannelCostVersionStatus.REJECTED.getCode());
        submit(1, MAKER_ID).assertBusinessRejected(ChannelCostVersionStatus.REJECTED.getCode());

        getVersion(1).assertSuccess(200).assertText("status", ChannelCostVersionStatus.REJECTED.getCode());
        assertPlanRow(PLAN_CODE, ChannelCostPlanStatus.DRAFT.getCode(), null, 0L);
    }

    /** 已有 DRAFT 或 PENDING_APPROVAL 版本时再创建被拒，不新增版本。 */
    @Test
    void test_pendingVersionBlocksCreate() {
        createVersion(version()).assertSuccess(201);
        createVersion(version()).assertBusinessRejected(PLAN_CODE);

        submit(1, MAKER_ID).assertSuccess(200);
        createVersion(version()).assertBusinessRejected(PLAN_CODE);

        assertEquals(1, testMapper.countChannelCostVersions(PLAN_CODE));
    }

    /** 方案摘要按版本号降序列出驳回、生效与草稿版本。 */
    @Test
    void test_queryVersionsDescending() {
        String rejectedHash = createSubmitted(version()).text("contentHash");
        reject(1, CHECKER_ID, rejectedHash).assertSuccess(200);
        createApproved(version());
        createVersion(version()).assertSuccess(201);

        JsonNode versions = getPlan(PLAN_CODE).assertSuccess(200).assertText("currentCostVer", "2").json()
            .path("versions");

        assertEquals(3, versions.size());
        assertSummary(versions.get(0), 3, ChannelCostVersionStatus.DRAFT, null);
        assertSummary(versions.get(1), 2, ChannelCostVersionStatus.ACTIVE, null);
        assertSummary(versions.get(2), 1, ChannelCostVersionStatus.REJECTED, null);
        assertEquals(CHECKER_ID, versions.get(2).path("checkerId").asString());
    }

    /** 版本号不存在：查询与提交为业务错误。 */
    @Test
    void test_versionNotFound() {
        getVersion(9).assertBusinessRejected("渠道成本版本不存在");
        submit(9, MAKER_ID).assertBusinessRejected("渠道成本版本不存在");
    }

    /** 模板单范围：六维、协议引用与处理费组成逐值回显。 */
    private void assertStandardScope(JsonNode scopes) {
        assertEquals(1, scopes.size(), "范围数");
        JsonNode scope = scopes.get(0);
        assertAll("范围 1",
            () -> assertEquals(1, scope.path("scopeNo").asInt()),
            () -> assertEquals(ChannelCostVersionRequestBuilder.MID_ID, scope.path("midId").asString()),
            () -> assertEquals(ChannelCostVersionRequestBuilder.ENTITY_NO, scope.path("entityNo").asString()),
            () -> assertEquals(ChannelCostVersionRequestBuilder.AGREEMENT_REF,
                scope.path("channelAgreementRef").asString()),
            () -> assertEquals(ChannelCostVersionRequestBuilder.COUNTRY_CODE, scope.path("countryCode").asString()),
            () -> assertEquals(ChannelCostVersionRequestBuilder.CURRENCY, scope.path("currency").asString()),
            () -> assertEquals(ChannelCostVersionRequestBuilder.PAYMENT_METHOD,
                scope.path("paymentMethodCode").asString()),
            () -> assertEquals(ChannelCostVersionRequestBuilder.ACTION_TYPE, scope.path("actionType").asString()));
        JsonNode components = scope.path("components");
        assertEquals(1, components.size(), "组成数");
        JsonNode component = components.get(0);
        assertAll("组成 PROCESSING",
            () -> assertEquals(ChannelCostVersionRequestBuilder.PROCESSING, component.path("componentCode").asString()),
            () -> assertEquals(ChannelCostComponentType.PROCESSING_FEE.getCode(),
                component.path("componentType").asString()),
            () -> assertEquals(ChannelCostCalculationBase.TRANSACTION_AMOUNT.getCode(),
                component.path("calculationBase").asString()),
            () -> assertDecimal(ChannelCostVersionRequestBuilder.PROCESSING_RATE, component.path("rateValue")),
            () -> assertDecimal(ChannelCostVersionRequestBuilder.PROCESSING_FIXED, component.path("fixedAmount")),
            () -> assertEquals(ChannelCostVersionRequestBuilder.CURRENCY, component.path("currency").asString()),
            () -> assertTrue(component.path("minAmount").isNull() || component.path("minAmount").isMissingNode()),
            () -> assertTrue(component.path("maxAmount").isNull() || component.path("maxAmount").isMissingNode()),
            () -> assertEquals(AmountRoundingMode.HALF_UP.getCode(), component.path("roundingMode").asString()));
    }

    /** 十进制字符串按数值比较。 */
    private static void assertDecimal(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual.asString())),
            "期望 " + expected + "，实际 " + actual);
    }

    /** 版本摘要断言；expectedEffectiveTo 为 null 表示未失效。 */
    private void assertSummary(JsonNode node, int costVer, ChannelCostVersionStatus status,
        Instant expectedEffectiveTo) {
        JsonNode effectiveTo = node.path("effectiveTo");
        assertAll("版本 " + costVer,
            () -> assertEquals(costVer, node.path("costVer").asInt()),
            () -> assertEquals(status.getCode(), node.path("status").asString()),
            () -> assertTrue(node.path("contentHash").asString().matches(HASH_PATTERN), "contentHash"),
            () -> assertEquals(expectedEffectiveTo,
                effectiveTo.isMissingNode() || effectiveTo.isNull() ? null : Instant.parse(effectiveTo.asString())));
    }
}
