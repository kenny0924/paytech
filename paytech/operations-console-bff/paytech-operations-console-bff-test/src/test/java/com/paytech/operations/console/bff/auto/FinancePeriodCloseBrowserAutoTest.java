package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.AUTHORIZE_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.FINANCE_APPROVE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.FINANCE_MANAGE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.FINANCE_READ_PERMISSION_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.ledger.api.enums.LedgerErrorCode;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.ext.rest.FinancePeriodCloseController;
import com.paytech.operations.console.bff.service.processor.PeriodCloseBoardProcessor;
import com.paytech.operations.console.bff.test.base.FinanceBrowserAutoTestBase;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RecordedRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 转发期间关账与重开的 6 个端点（P1—P6），并验证 BFF 聚合的期间看板（F1）；ledger 由 Loopback Simulator 承担
 * <li>发起关账、发起重开与审批使用长耗时读取超时（测试配置 5 秒），其余调用保持 4 秒。</li>
 *
 * @see FinancePeriodCloseController
 * @see PeriodCloseBoardProcessor
 * @author Claude
 */
class FinancePeriodCloseBrowserAutoTest extends FinanceBrowserAutoTestBase {
    private static final String BOUNDARY = "/finance-period-close-boundary.json";
    private static final String CALENDAR_ID = "7312345678901235001";
    private static final String PERIOD_ID = "7312345678901236010";
    private static final String NEXT_PERIOD_ID = "7312345678901236011";
    private static final String HASH = "9f2c0e6b4d1a7c35e8b2f0d94a6c1e7b3d5f8a2c4e6b0d1f3a5c7e9b2d4f6a8c";

    static Stream<JsonNode> endpoints() {
        return rows(BOUNDARY);
    }

    /** 方法、路径、Query、命令正文与操作人 Header 准确转发；发起关账不带 Body。 */
    @ParameterizedTest(name = "{index} 期间关账转发")
    @MethodSource("endpoints")
    void test_normalFlow(JsonNode row) throws Exception {
        replyAsOwner(ledger, row);

        assertForwarded(ledger, row, send(row));
    }

    /** 当前会话授权失败时不访问 ledger。 */
    @ParameterizedTest(name = "{index} 授权拒绝")
    @MethodSource("endpoints")
    void test_authorizationDenied(JsonNode row) throws Exception {
        platform.allow(false);

        assertDenied(ledger, send(row));
    }

    /** 发起需要 MANAGE，审批与驳回需要 APPROVE。 */
    @ParameterizedTest
    @CsvSource({"P3,READ", "P4,APPROVE", "P5,MANAGE", "P6,MANAGE"})
    void test_separatedPermissions(String id, String granted) throws Exception {
        platform.grantPermissions(FINANCE_READ_PERMISSION_ID, switch (granted) {
            case "MANAGE" -> FINANCE_MANAGE_PERMISSION_ID;
            case "APPROVE" -> FINANCE_APPROVE_PERMISSION_ID;
            default -> FINANCE_READ_PERMISSION_ID;
        });

        assertDenied(ledger, send(row(BOUNDARY, id)));
    }

    /** 发起、重开与复核的 LedgerErrorCode 拒绝（D4）Code 与默认消息原样到达，不外露 ledger 原文。 */
    @ParameterizedTest(name = "{index} {0} {1}")
    @CsvSource({"P3,PERIOD_NOT_OPEN", "P3,PERIOD_CLOSE_ORDER_INVALID", "P4,PERIOD_REOPEN_NOT_ALLOWED",
        "P5,SELF_REVIEW_FORBIDDEN", "P5,REVIEW_CONTENT_CHANGED", "P5,REVIEW_STATUS_INVALID", "P6,SELF_REVIEW_FORBIDDEN",
        "P6,REVIEW_CONTENT_CHANGED", "P6,REVIEW_STATUS_INVALID"})
    void test_businessRuleViolation(String id, LedgerErrorCode code) throws Exception {
        var row = row(BOUNDARY, id);
        ledger.problem("POST", ownerPath(row), 400, code.getCode());

        assertCodedRejection(code, send(row));
    }

    /** 看板按 ledger 期间顺序组装：已有流程的期间带序号最大的一条，未发起的期间 latestClose 为空；只读、不带操作人。 */
    @Test
    void test_boardCombinesPeriodsAndLatestClose() throws Exception {
        var closes = row(BOUNDARY, "P1");
        ledger.reply("GET", periodsPath(), 200, JSON.writeValueAsString(periods(2)));
        replyAsOwner(ledger, closes);
        ledger.reply("GET", "/accounting-periods/v1/" + NEXT_PERIOD_ID + "/closes", 200,
            "{\"items\":[],\"page\":1,\"size\":10,\"total\":0}");

        var response = sendBoard("?fiscalCalendarId=" + CALENDAR_ID);

        assertEquals(200, response.statusCode(), response.body());
        var board = JSON.readTree(response.body());
        var items = board.path("periods");
        assertAll(() -> assertEquals(CALENDAR_ID, board.path("fiscalCalendarId").asString()),
            () -> assertEquals(2, items.size()),
            () -> assertEquals(periods(2).path("items").get(0), items.get(0).path("period")),
            () -> assertEquals(closes.path("response").path("items").get(0), items.get(0).path("latestClose")),
            () -> assertEquals(NEXT_PERIOD_ID, items.get(1).path("period").path("accountingPeriodId").asString()),
            () -> assertTrue(items.get(1).path("latestClose").isNull()));
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
        var calls = ledger.requests();
        assertAll(() -> assertEquals(3, calls.size(), calls.toString()),
            () -> assertEquals(periodsPath(), calls.get(0).path()),
            () -> assertEquals(Map.of("page", "1", "size", "200"), query(calls.get(0).query())),
            () -> assertEquals(Map.of("page", "1", "size", "10"), query(calls.get(1).query())),
            () -> assertTrue(calls.stream().allMatch(call -> call.header("X-Gateway-Actor-Subject") == null)));
    }

    /** 日历不存在时 ledger 的合同 Code 原样到达，不再读取流程；流程读取失败时整页失败。 */
    @Test
    void test_boardFailsAsWhole() throws Exception {
        ledger.problem("GET", periodsPath(), 400, LedgerSetupErrorCode.FISCAL_CALENDAR_NOT_FOUND.getCode());
        assertCodedRejection(LedgerSetupErrorCode.FISCAL_CALENDAR_NOT_FOUND, sendBoard("?fiscalCalendarId=" + CALENDAR_ID));
        assertEquals(1, ledger.requests().size());

        resetBetweenCalls();
        ledger.reply("GET", periodsPath(), 200, JSON.writeValueAsString(periods(2)));
        ledger.problem("GET", "/accounting-periods/v1/" + PERIOD_ID + "/closes", 400,
            CommonErrorCode.BUSINESS_CHECK_FAILED.getCode());
        assertCodedRejection(CommonErrorCode.BUSINESS_CHECK_FAILED, sendBoard("?fiscalCalendarId=" + CALENDAR_ID));
        assertEquals(2, ledger.requests().size());
    }

    /** 看板缺少或非法日历、多余 Query、带 Body 均在访问 ledger 前拒绝。 */
    @Test
    void test_boardInvalidParameter() throws Exception {
        assertRejectedLocally(400, ledger, sendBoard(""));

        resetBetweenCalls();
        assertRejectedLocally(400, ledger, sendBoard("?fiscalCalendarId=0"));

        resetBetweenCalls();
        assertRejectedLocally(400, ledger, sendBoard("?fiscalCalendarId=" + CALENDAR_ID + "&page=1"));
    }

    /** 发起关账、发起重开与审批在 ledger 同步执行：读取等待超过普通 4 秒，到长耗时超时后映射 504 DEPENDENCY_TIMEOUT。 */
    @ParameterizedTest
    @CsvSource({"P3", "P4", "P5"})
    void test_longRunningCommandTimeout(String id) throws Exception {
        var row = row(BOUNDARY, id);
        ledger.hang("POST", ownerPath(row));

        long started = System.nanoTime();
        var response = send(row);
        var elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertEquals(504, response.statusCode(), response.body());
        assertEquals(OperationsBffErrorCode.DEPENDENCY_TIMEOUT.getCode(), JSON.readTree(response.body()).path("code").asString());
        assertTrue(elapsed.compareTo(Duration.ofMillis(4500)) > 0, "长耗时调用不得使用普通 4 秒超时: " + elapsed);
        assertEquals(1, ledger.requests().size());
    }

    /** 驳回与查询保持普通 4 秒读取超时。 */
    @Test
    void test_rejectUsesStandardTimeout() throws Exception {
        var row = row(BOUNDARY, "P6");
        ledger.hang("POST", ownerPath(row));

        long started = System.nanoTime();
        var response = send(row);
        var elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertEquals(504, response.statusCode(), response.body());
        assertTrue(elapsed.compareTo(Duration.ofMillis(4900)) < 0, "驳回应使用普通读取超时: " + elapsed);
    }

    /** 发起关账带 Body、非法流程序号、重开缺申请编号、非法确认摘要均在访问 ledger 前拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        assertRejectedLocally(400, ledger, send(row(BOUNDARY, "P3"), "", JSON.createObjectNode(), true));

        resetBetweenCalls();
        var detail = (ObjectNode) row(BOUNDARY, "P2").deepCopy();
        detail.put("path", "/finance/accounting-periods/v1/" + PERIOD_ID + "/closes/0");
        assertRejectedLocally(400, ledger, send(detail, "", detail.path("request"), true));

        resetBetweenCalls();
        var reopen = row(BOUNDARY, "P4");
        var noRef = (ObjectNode) reopen.path("request").deepCopy();
        noRef.remove("requestRef");
        assertRejectedLocally(422, ledger, send(reopen, "", noRef, true));

        resetBetweenCalls();
        var approve = row(BOUNDARY, "P5");
        assertRejectedLocally(422, ledger, send(approve, "", JSON.readTree("{\"approvedCloseHash\":\"" + HASH.toUpperCase()
            + "\"}"), true));

        resetBetweenCalls();
        var list = row(BOUNDARY, "P1");
        assertRejectedLocally(400, ledger, send(list, "?page=1&size=7", list.path("request"), true));
    }

    /** 列表元素不属于请求期间、详情或复核回执的期间/序号不一致、详情步骤缺失时视为上游无效响应 502。 */
    @Test
    void test_invalidUpstreamResponse() throws Exception {
        var list = row(BOUNDARY, "P1");
        var foreign = (ObjectNode) list.path("response").deepCopy();
        ((ObjectNode) foreign.path("items").get(1)).put("periodId", NEXT_PERIOD_ID);
        ledger.reply("GET", ownerPath(list), 200, JSON.writeValueAsString(foreign));
        assertEquals(502, send(list).statusCode());

        resetBetweenCalls();
        var detail = row(BOUNDARY, "P2");
        var noSteps = (ObjectNode) detail.path("response").deepCopy();
        noSteps.putNull("steps");
        ledger.reply("GET", ownerPath(detail), 200, JSON.writeValueAsString(noSteps));
        assertEquals(502, send(detail).statusCode());

        resetBetweenCalls();
        var approve = row(BOUNDARY, "P5");
        var otherClose = (ObjectNode) approve.path("response").deepCopy();
        otherClose.put("closeNo", "3");
        ledger.reply("POST", ownerPath(approve), 200, JSON.writeValueAsString(otherClose));
        assertEquals(502, send(approve).statusCode());

        resetBetweenCalls();
        var start = row(BOUNDARY, "P3");
        var otherPeriod = (ObjectNode) start.path("response").deepCopy();
        otherPeriod.put("periodId", NEXT_PERIOD_ID);
        ledger.reply("POST", ownerPath(start), 201, JSON.writeValueAsString(otherPeriod));
        assertEquals(502, send(start).statusCode());
    }

    /** 失败的关账回执原样返回首个失败步骤，closeHash 为空。 */
    @Test
    void test_failedCloseForwarded() throws Exception {
        var start = row(BOUNDARY, "P3");
        var failed = (ObjectNode) start.path("response").deepCopy();
        failed.put("status", "FAILED");
        failed.putNull("closeHash");
        failed.put("failedStepType", "LEDGER_RECONCILIATION");
        ledger.reply("POST", ownerPath(start), 201, JSON.writeValueAsString(failed));

        var body = JSON.readTree(send(start).body());

        assertAll(() -> assertEquals("FAILED", body.path("status").asString()),
            () -> assertTrue(body.path("closeHash").isNull()),
            () -> assertEquals("LEDGER_RECONCILIATION", body.path("failedStepType").asString()),
            () -> assertFalse(body.has("steps")));
    }

    private static String periodsPath() {
        return "/fiscal-calendars/v1/" + CALENDAR_ID + "/periods";
    }

    /** ledger 日历期间分页（size 200），按财年、期号升序。 */
    private static JsonNode periods(int count) {
        var items = JSON.createArrayNode();
        for (int i = 0; i < count; i++) {
            items.add(JSON.createObjectNode().put("accountingPeriodId", i == 0 ? PERIOD_ID : NEXT_PERIOD_ID)
                .put("fiscalCalendarId", CALENDAR_ID).put("fiscalYear", 2026).put("periodNo", 10 + i)
                .put("startDate", "2026-" + (10 + i) + "-01").put("endDate", i == 0 ? "2026-10-31" : "2026-11-30")
                .put("status", i == 0 ? "OPEN" : "FUTURE").put("reopenCount", 0));
        }
        return JSON.createObjectNode().<ObjectNode>set("items", items).put("page", 1).put("size", 200).put("total", count);
    }

    /** 经真实 BFF 请求看板。 */
    private HttpResponse<String> sendBoard(String query) throws Exception {
        var board = JSON.createObjectNode().put("method", "GET").put("path", "/finance/period-close/v1/board")
            .put("routeCode", "operations-finance-period-close-board");
        return send(board, query, JSON.nullNode(), true);
    }
}
