package com.paytech.commercial.auto.ext.http.term;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.BandBasis;
import com.paytech.commercial.api.event.CommercialTermVersionActivatedPayload;
import com.paytech.commercial.api.term.CommercialTermApi;
import com.paytech.commercial.api.term.request.CommercialTermVersionCommandRequestBody;
import com.paytech.commercial.api.term.request.CreateCommercialTermPlanRequestBody;
import com.paytech.commercial.domain.enums.BusinessChangeReason;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.commercial.service.handler.CommercialTermVersionActivatedHandler;
import com.paytech.commercial.test.base.term.AbstractCommercialTermApiTest;
import com.paytech.foundation.common.error.CommonErrorCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 商业条款写入入口（technique.md §2.2、§2.11 写入矩阵）：方案 / 版本 / 价格集合 / 提交 / 审批 / 排期 / 激活经真实 HTTP、Processor、
 * Redis 资源锁与本地 MySQL
 * <li>覆盖首次处理、同身份同内容 / 改内容重放、归属错误、状态不符、BCR 版本冲突、保存回滚、maker = checker、提交后事件发布。</li>
 *
 * @see CommercialTermApi
 */
class CommercialTermWriteHttpTest extends AbstractCommercialTermApiTest {
    /** 每例：目录、ACTIVE 合同、三个 ACTIVE 卡画像、CARD_VISA（USD）。 */
    @BeforeEach
    void setUpClassMocks() {
        mockBaseline();
    }

    /** 首次处理：方案 → 版本 → 价格 → 提交 → 批准 → 排期 → 激活；BCR 根逐步推进、快照记 Maker / Checker，提交后发布事件。 */
    @Test
    void test_normalFlow() {
        JsonNode plan = createPlan();
        JsonNode version = createVersion(plan);
        JsonNode priced = replacePrices(version, price("USD", CARD, 1, percentage("MDR", "0.025")));
        JsonNode submitted = command(priced, "submit", MAKER);
        JsonNode approvedVersion = command(submitted, "approve", CHECKER);
        JsonNode signedVersion = signed(approvedVersion);
        JsonNode scheduled = ok(200, scheduleResult(signedVersion, NOW));
        JsonNode active = command(scheduled, "activate", CHECKER);

        long planId = Long.parseLong(text(plan, "planId"));
        long versionId = Long.parseLong(text(version, "versionId"));
        assertAll(() -> assertEquals("1", text(plan, "changeVersion")), () -> assertEquals("DRAFT", text(plan, "status")),
            () -> assertEquals("1", text(version, "versionNo")), () -> assertEquals("2", text(version, "changeVersion")),
            () -> assertEquals("PENDING_APPROVAL", text(submitted, "status")),
            () -> assertEquals("APPROVED", text(approvedVersion, "status")),
            () -> assertEquals("CLIENT_SIGNED", text(signedVersion, "status")),
            () -> assertEquals("SIGN-" + versionId, signedVersion.path("acceptance").path("acceptanceReference").asString()),
            () -> assertEquals("SCHEDULED", text(scheduled, "status")),
            () -> assertEquals(NOW.toString(), text(scheduled, "effectiveFrom")),
            () -> assertEquals("ACTIVE", text(active, "status")), () -> assertEquals("8", text(active, "changeVersion")),
            () -> assertEquals("ACTIVE", testMapper.termPlanStatus(planId)),
            () -> assertEquals(Boolean.TRUE, testMapper.termVersion(versionId).get("active_slot")));

        // 版本 Child 快照：CREATE、价格替换、提交（Maker）、批准（Checker）、客户签署、排期、激活
        List<Map<String, Object>> snapshots = testMapper.businessChangeSnapshots("COMMERCIAL_TERM_VERSION", versionId);
        assertAll(() -> assertEquals(List.of(2L, 3L, 4L, 5L, 6L, 7L, 8L),
                snapshots.stream().map(row -> ((Number)row.get("version_no")).longValue()).toList()),
            () -> assertEquals(MAKER, ((Number)snapshots.get(2).get("create_by")).longValue()),
            () -> assertEquals(BusinessChangeReason.TERM_VERSION_SUBMITTED.getCode(), snapshot(snapshots.get(2)).path("_meta").path("reasonCode").asString()),
            () -> assertEquals(CHECKER, ((Number)snapshots.get(3).get("create_by")).longValue()),
            () -> assertEquals(Long.toString(MAKER), snapshot(snapshots.get(3)).path("data").path("makerAccountId").asString()),
            () -> assertEquals(Long.toString(CHECKER),
                snapshot(snapshots.get(3)).path("data").path("checkerAccountId").asString()),
            () -> assertEquals(BusinessChangeReason.TERM_VERSION_ACCEPTED.getCode(),
                snapshot(snapshots.get(4)).path("_meta").path("reasonCode").asString()),
            () -> assertEquals(8L, testMapper.businessChangeVersion("COMMERCIAL_TERM_PLAN", planId)));

        // 提交后发布：信封版本为本次激活后的方案根版本，首次生效无被替代版本
        String eventKey = CommercialTermVersionActivatedHandler.buildEvent(planId, 8).eventKey();
        JsonNode envelope = events.awaitEnvelope(CommercialTermVersionActivatedHandler.EVENT_TYPE, eventKey);
        JsonNode payload = envelope.path("payload");
        assertAll(() -> assertEquals(Long.toString(versionId), payload.path("versionId").asString()),
            () -> assertEquals(CommercialTermVersionActivatedPayload.EVENT_TYPE, envelope.path("eventType").asString()),
            () -> assertTrue(payload.path("supersededVersionId").isNull()),
            () -> assertEquals("8", payload.path("changeVersion").asString()));
    }

    /** 方案同身份重放：同内容返回原方案 200；同编号不同 Owner 409；合同已有该条款类型方案时换编号 400。 */
    @Test
    void test_planReplay() {
        JsonNode first = createPlan();

        JsonNode replay = ok(200, http.post(TERMS + "/plans", headers(MAKER), planRequest()));
        CreateCommercialTermPlanRequestBody otherOwner = planRequest();
        otherOwner.setClientId("930000000000999");
        CreateCommercialTermPlanRequestBody otherCode = planRequest();
        otherCode.setPlanCode("PLAN-C-2");

        assertEquals(text(first, "planId"), text(replay, "planId"));
        assertProblem(http.post(TERMS + "/plans", headers(MAKER), otherOwner), 409, CommonErrorCode.CONCURRENT_REQUEST.getCode(),
            "不同归属");
        assertProblem(http.post(TERMS + "/plans", headers(MAKER), otherCode), 400,
            CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), "已有同条款类型方案");
    }

    /** 操作者取 X-Gateway-Actor-Subject：缺失或非规范十进制 400，落库前拒绝。 */
    @Test
    void test_invalidActorHeader() {
        assertProblem(http.post(TERMS + "/plans", headers(), planRequest()), 400, CommonErrorCode.INVALID_REQUEST.getCode(),
            "");
        JsonNode version = createVersion(createPlan());
        HttpHeaders malformed = headers();
        malformed.set(CommercialTermApi.ACTOR_HEADER, "01");
        CommercialTermVersionCommandRequestBody body = new CommercialTermVersionCommandRequestBody();
        body.setExpectedChangeVersion(text(version, "changeVersion"));
        assertProblem(http.post(TERMS + "/versions/" + text(version, "versionId") + "/submit", malformed, body), 400,
            CommonErrorCode.INVALID_REQUEST.getCode(), "X-Gateway-Actor-Subject");
        assertEquals(2L, testMapper.businessChangeVersion("COMMERCIAL_TERM_PLAN", Long.parseLong(text(version, "planId"))));
    }

    /** 归属错误：Owner 二选一违反 400；Owner 与合同 Client 不符 400；价格 OE 不被合同覆盖 400，价格集合不变。 */
    @Test
    void test_ownershipMismatch() {
        CreateCommercialTermPlanRequestBody bothOwners = planRequest();
        bothOwners.setLocalPartnerId("930000000000998");
        CreateCommercialTermPlanRequestBody wrongClient = planRequest();
        wrongClient.setClientId("930000000000999");
        assertProblem(http.post(TERMS + "/plans", headers(MAKER), bothOwners), 400, CommonErrorCode.INVALID_REQUEST.getCode(),
            "恰好一个");
        assertProblem(http.post(TERMS + "/plans", headers(MAKER), wrongClient), 400,
            CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), "Owner 不符");

        JsonNode version = createVersion(createPlan());
        var uncovered = price("USD", CARD, 1, percentage("MDR", "0.025"));
        uncovered.setEntityNo("AE001");
        assertProblem(replacePricesResult(version, uncovered), 400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(),
            "合同不覆盖 OE");
        assertEquals(List.of(0, 0, 0), testMapper.countPriceSet(Long.parseLong(text(version, "versionId"))));
    }

    /** 写入校验：未登记计费事件、组件矩阵不符、Partner 才能用的计算基数、同 OE 币种优先级重复，均 400。 */
    @Test
    void test_priceSetValidation() {
        JsonNode version = createVersion(createPlan());
        var unknownEvent = percentage("MDR", "0.025");
        unknownEvent.setTriggerCode(CommercialCodes.FeeEvent.PAYOUT_SUBMITTED);
        var fixedWithoutCurrency = fixed("FEE", "0.30", null);
        var buyRate = percentage("MDR", "0.025");
        buyRate.setCalculationBase("BUY_RATE_DIFFERENCE");

        assertAll(
            () -> assertProblem(replacePricesResult(version, price("USD", CARD, 1, unknownEvent)), 400,
                CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), "计费事件未在条款类型"),
            () -> assertProblem(replacePricesResult(version, price("USD", CARD, 1, fixedWithoutCurrency)), 400,
                CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), "FIXED 组件"),
            () -> assertProblem(replacePricesResult(version, price("USD", CARD, 1, buyRate)), 400,
                CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), "BUY_RATE_DIFFERENCE"),
            () -> assertProblem(replacePricesResult(version, price("USD", CARD, 1, percentage("MDR", "0.02")),
                price("USD", CARD_VISA, 1, percentage("MDR", "0.02"))), 400,
                CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), "优先级重复"));
    }

    /** 状态不符：非 DRAFT 不能改价或再提交，DRAFT 不能批准，没有价格不能提交，均 400。 */
    @Test
    void test_stateMismatch() {
        JsonNode version = createVersion(createPlan());
        assertProblem(commandResult(version, "submit", MAKER), 400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(),
            "没有价格");
        assertProblem(commandResult(version, "approve", CHECKER), 400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(),
            "PENDING_APPROVAL");

        JsonNode submitted = command(replacePrices(version, price("USD", CARD, 1, percentage("MDR", "0.025"))), "submit",
            MAKER);
        assertProblem(replacePricesResult(submitted, price("USD", CARD, 1, percentage("MDR", "0.03"))), 400,
            CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), "只有 DRAFT");
        assertProblem(commandResult(submitted, "submit", MAKER), 400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(),
            "只有 DRAFT");
    }

    /** BCR 版本冲突：过期的 expectedChangeVersion 409，数据不变。 */
    @Test
    void test_changeVersionConflict() {
        JsonNode plan = createPlan();
        JsonNode version = createVersion(plan);

        var stale = createVersionResult(text(plan, "planId"), text(plan, "changeVersion"), null);

        assertProblem(stale, 409, CommonErrorCode.CONCURRENT_REQUEST.getCode(), "业务版本已变化");
        assertProblem(replacePricesResult(((ObjectNode)plan.deepCopy()).put("versionId", text(version, "versionId")),
            price("USD", CARD, 1, percentage("MDR", "0.025"))), 409, CommonErrorCode.CONCURRENT_REQUEST.getCode(),
            "业务版本已变化");
        assertEquals(2L, testMapper.businessChangeVersion("COMMERCIAL_TERM_PLAN", Long.parseLong(text(plan, "planId"))));
    }

    /** Maker = Checker：批准与驳回均 400；Checker 驳回回到 DRAFT，可再改价。 */
    @Test
    void test_makerEqualsChecker() {
        JsonNode submitted = command(replacePrices(createVersion(createPlan()),
            price("USD", CARD, 1, percentage("MDR", "0.025"))), "submit", MAKER);

        assertProblem(commandResult(submitted, "approve", MAKER), 400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(),
            "审批人不能是提交人");
        assertProblem(commandResult(submitted, "reject", MAKER), 400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(),
            "审批人不能是提交人");
        JsonNode rejected = command(submitted, "reject", CHECKER);

        assertEquals(CommercialTermVersionStatus.DRAFT.getCode(), text(rejected, "status"));
        replacePrices(rejected, price("USD", CARD, 1, percentage("MDR", "0.03")));
    }

    /** 保存回滚：根 CAS、删旧价与插新价之后审计快照唯一键冲突，整个事务回滚，根版本与原价格集合不变。 */
    @Test
    void test_saveRollback() {
        JsonNode plan = createPlan();
        JsonNode version = replacePrices(createVersion(plan), price("USD", CARD, 1, percentage("MDR", "0.025")));
        long versionId = Long.parseLong(text(version, "versionId"));
        testMapper.insertBusinessChangeSnapshot("COMMERCIAL_TERM_VERSION", versionId, 4);

        var result = replacePricesResult(version, price("USD", CARD, 1, percentage("MDR", "0.03")),
            price("USD", CARD_VISA, 2, fixed("FEE", "0.30", "USD")));

        assertEquals(500, result.getStatus().value());
        assertAll(() -> assertEquals(3L, testMapper.businessChangeVersion("COMMERCIAL_TERM_PLAN",
                Long.parseLong(text(plan, "planId")))),
            () -> assertEquals(3L, testMapper.businessChangeVersion("COMMERCIAL_TERM_VERSION", versionId)),
            () -> assertEquals(List.of(1, 1, 0), testMapper.countPriceSet(versionId)));
    }

    /** 复制版本：第 2 版复制第 1 版的价格、组件与档位（新 ID），版本号递增；来源版本不存在 400。 */
    @Test
    void test_copyFromVersion() {
        JsonNode plan = createPlan();
        JsonNode first = replacePrices(createVersion(plan), price("USD", CARD, 1, percentage("MDR", "0.025")),
            price("USD", CARD_VISA, 2, tiered("TIER", "USD", BandBasis.TRANSACTION_AMOUNT,
                "WHOLE_BASE", band("0", "100", "0.03"), band("100", null, "0.02"))));

        JsonNode second = ok(201, createVersionResult(text(plan, "planId"), text(first, "changeVersion"),
            text(first, "versionId")));
        var crossPlan = createVersionResult(text(plan, "planId"), text(second, "changeVersion"), "930000000000777");

        assertAll(() -> assertEquals("2", text(second, "versionNo")),
            () -> assertEquals(List.of(2, 2, 2), testMapper.countPriceSet(Long.parseLong(text(second, "versionId")))),
            () -> assertEquals(List.of(2, 2, 2), testMapper.countPriceSet(Long.parseLong(text(first, "versionId")))));
        assertProblem(crossPlan, 400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), "不存在");
        assertNull(testMapper.businessChangeVersion("COMMERCIAL_TERM_VERSION", 930000000000777L));
    }

    /** 快照 JSON。 */
    JsonNode snapshot(Map<String, Object> row) {
        return json.readTree(row.get("snapshot_json").toString());
    }
}
