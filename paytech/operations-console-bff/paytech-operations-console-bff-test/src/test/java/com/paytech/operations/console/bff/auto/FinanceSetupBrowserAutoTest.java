package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.FINANCE_READ_PERMISSION_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.ext.rest.FinanceSetupController;
import com.paytech.operations.console.bff.test.base.FinanceBrowserAutoTestBase;
import com.paytech.operations.console.bff.test.simulator.FinanceOwnerSimulator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 与 HttpExchange 转发账务基础资料的 14 个端点（R01—R07、R14—R15、科目维护 L1—L5）；ledger 由 Loopback Simulator 承担
 * <li>R16 币种下拉改读 Platform，见 {@link CurrencySpecBrowserAutoTest}。</li>
 *
 * @see FinanceSetupController
 * @author Claude
 */
class FinanceSetupBrowserAutoTest extends FinanceBrowserAutoTestBase {
    private static final String BOUNDARY = "/finance-setup-boundary.json";

    static Stream<JsonNode> endpoints() {
        return rows(BOUNDARY);
    }

    /** 方法、路径、Query、命令正文与操作人 Header 准确转发，状态码与响应原样返回。 */
    @ParameterizedTest(name = "{index} 账务基础资料转发")
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

    /** 只有 READ 权限的操作人能查询（含科目变更记录），不能执行 MANAGE 命令（含科目维护）。 */
    @ParameterizedTest
    @CsvSource({"R02,R01", "L1,L5", "L2,R15", "L3,L5", "L4,R15"})
    void test_readOnlyOperatorCannotManage(String commandId, String queryId) throws Exception {
        platform.grantPermissions(FINANCE_READ_PERMISSION_ID);

        assertDenied(ledger, send(row(BOUNDARY, commandId)));

        resetBetweenCalls();
        platform.grantPermissions(FINANCE_READ_PERMISSION_ID);
        var list = row(BOUNDARY, queryId);
        replyAsOwner(ledger, list);
        assertForwarded(ledger, list, send(list));
    }

    /** ledger 登记的业务拒绝以 400 与枚举 Code 原样到达浏览器，供页面落到对应字段。 */
    @ParameterizedTest
    @CsvSource({"R02,FINANCE_ENTITY_ALREADY_EXISTS", "R04,FISCAL_CALENDAR_ALREADY_EXISTS",
        "R04,FINANCE_ENTITY_UNAVAILABLE", "R05,FISCAL_CALENDAR_NOT_FOUND", "R06,ACCOUNTING_PERIOD_OVERLAP",
        "R06,ACCOUNTING_PERIOD_NO_DUPLICATE", "R07,ACCOUNTING_PERIOD_NOT_FOUND", "R07,ACCOUNTING_PERIOD_NOT_FUTURE",
        "R15,CHART_NOT_ACTIVE", "L1,CHART_NOT_ACTIVE", "L1,CHART_ACCOUNT_NO_INVALID", "L1,CHART_ACCOUNT_NO_DUPLICATE",
        "L1,CHART_ACCOUNT_CODE_DUPLICATE", "L1,CHART_PARENT_INVALID", "L2,CHART_ACCOUNT_NOT_FOUND",
        "L2,CHART_STRUCTURE_FROZEN", "L2,CHART_PARENT_INVALID", "L3,CHART_ACCOUNT_IN_USE", "L4,CHART_PARENT_INVALID",
        "L5,CHART_ACCOUNT_NOT_FOUND"})
    void test_businessRuleViolation(String id, LedgerSetupErrorCode code) throws Exception {
        var row = row(BOUNDARY, id);
        ledger.problem(row.path("method").asString(), ownerPath(row), 400, code.getCode());

        assertCodedRejection(code, send(row));
    }

    /** 未登记的公共业务拒绝保留公共 Code 与默认说明，不外露 ledger 原文。 */
    @Test
    void test_commonBusinessCheckFailed() throws Exception {
        var row = row(BOUNDARY, "R04");
        ledger.problem("POST", ownerPath(row), 400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode());

        var response = send(row);

        assertEquals(400, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), body.path("code").asString()),
            () -> assertFalse(response.body().contains(FinanceOwnerSimulator.OWNER_DETAIL_CANARY)));
    }

    /** ledger 422 只保留本次请求体内的字段路径。 */
    @Test
    void test_validationPathsProjected() throws Exception {
        var row = row(BOUNDARY, "R06");
        String upstream = """
            {"type":"about:blank","title":"Unprocessable Content","detail":"%s","code":"VALIDATION_ERROR",
             "requestId":"%s","timestamp":"2026-10-18T06:00:00.000000Z","errors":[
              {"field":"startDate","message":"x"},{"field":"fiscalCalendarId","message":"y"}]}
            """.formatted(FinanceOwnerSimulator.OWNER_DETAIL_CANARY, FinanceOwnerSimulator.OWNER_REQUEST_ID);
        ledger.reply("POST", ownerPath(row), 422, "application/problem+json", upstream,
            Map.of("X-Request-Id", FinanceOwnerSimulator.OWNER_REQUEST_ID));

        var response = send(row);

        assertEquals(422, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(CommonErrorCode.VALIDATION_ERROR.getCode(), body.path("code").asString()),
            () -> assertEquals(1, body.path("errors").size()),
            () -> assertEquals("startDate", body.path("errors").get(0).path("field").asString()),
            () -> assertFalse(response.body().contains(FinanceOwnerSimulator.OWNER_DETAIL_CANARY)));
    }

    /** 非法分页、未知 Query、查询带 Body、开放期间带 Body、未知或强转 Body 字段、超限 Body、缺少 Origin 均在访问 ledger 前拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        var list = row(BOUNDARY, "R01");
        assertRejectedLocally(400, ledger, send(list, "?page=1&size=7", list.path("request"), true));

        resetBetweenCalls();
        var calendars = row(BOUNDARY, "R03");
        assertRejectedLocally(400, ledger, send(calendars, calendars.path("query").asString() + "&unknown=1",
            calendars.path("request"), true));

        resetBetweenCalls();
        var charts = row(BOUNDARY, "R14");
        assertRejectedLocally(400, ledger, send(charts, "", JSON.createObjectNode(), true));

        resetBetweenCalls();
        var open = row(BOUNDARY, "R07");
        assertRejectedLocally(400, ledger, send(open, "", JSON.createObjectNode(), true));

        resetBetweenCalls();
        var create = row(BOUNDARY, "R02");
        var unknown = (ObjectNode) create.path("request").deepCopy();
        unknown.put("status", "ACTIVE");
        assertRejectedLocally(400, ledger, send(create, "", unknown, true));

        resetBetweenCalls();
        var period = row(BOUNDARY, "R06");
        var coerced = (ObjectNode) period.path("request").deepCopy();
        coerced.put("periodNo", "12");
        assertRejectedLocally(400, ledger, send(period, "", coerced, true));

        resetBetweenCalls();
        var oversized = (ObjectNode) create.path("request").deepCopy();
        oversized.put("displayName", "x".repeat(8192));
        assertRejectedLocally(413, ledger, send(create, "", oversized, true));

        resetBetweenCalls();
        assertRejectedLocally(400, ledger, send(open, "?force=true", open.path("request"), true));

        resetBetweenCalls();
        var badPath = create.deepCopy();
        ((ObjectNode) badPath).put("path", "/finance/accounting-periods/v1/0123/open");
        ((ObjectNode) badPath).put("routeCode", "operations-finance-period-open");
        assertRejectedLocally(400, ledger, send(badPath, "", JSON.nullNode(), true));

        resetBetweenCalls();
        assertEquals(403, send(create, "", create.path("request"), false).statusCode());
        assertEquals(0, ledger.requests().size());
    }

    /** 科目维护：非法启停状态筛选、变更记录未知 Query、停用/启用带 Body、非法科目编号与上级编号、改科目类型均在访问 ledger 前拒绝。 */
    @Test
    void test_invalidChartMaintenanceParameter() throws Exception {
        var accounts = row(BOUNDARY, "R15");
        assertRejectedLocally(400, ledger, send(accounts, "?accountStatus=CLOSED", accounts.path("request"), true));

        resetBetweenCalls();
        var changes = row(BOUNDARY, "L5");
        assertRejectedLocally(400, ledger, send(changes, changes.path("query").asString() + "&changeType=UPDATE",
            changes.path("request"), true));

        resetBetweenCalls();
        assertRejectedLocally(400, ledger, send(row(BOUNDARY, "L3"), "", JSON.createObjectNode(), true));

        resetBetweenCalls();
        assertRejectedLocally(400, ledger, send(row(BOUNDARY, "L4"), "", JSON.createObjectNode(), true));

        resetBetweenCalls();
        var create = row(BOUNDARY, "L1");
        var badNo = (ObjectNode) create.path("request").deepCopy();
        badNo.put("accountNo", "22010");
        assertRejectedLocally(422, ledger, send(create, "", badNo, true));

        resetBetweenCalls();
        var leafParent = (ObjectNode) create.path("request").deepCopy();
        leafParent.put("parentAccountNo", "2201010101");
        assertRejectedLocally(422, ledger, send(create, "", leafParent, true));

        resetBetweenCalls();
        var update = row(BOUNDARY, "L2");
        var postingChange = (ObjectNode) update.path("request").deepCopy();
        postingChange.put("postingAllowed", false);
        assertRejectedLocally(400, ledger, send(update, "", postingChange, true));
    }

    /** 分页回执与请求不一致、命令回执身份不一致或缺主键时视为上游无效响应 502。 */
    @Test
    void test_invalidUpstreamResponse() throws Exception {
        var list = row(BOUNDARY, "R01");
        var echo = (ObjectNode) list.path("response").deepCopy();
        echo.put("page", 2);
        ledger.reply("GET", ownerPath(list), 200, JSON.writeValueAsString(echo));
        assertEquals(502, send(list).statusCode());

        resetBetweenCalls();
        var total = (ObjectNode) list.path("response").deepCopy();
        total.put("total", 0);
        ledger.reply("GET", ownerPath(list), 200, JSON.writeValueAsString(total));
        assertEquals(502, send(list).statusCode());

        resetBetweenCalls();
        var open = row(BOUNDARY, "R07");
        ledger.reply("POST", ownerPath(open), 200, "{\"accountingPeriodId\":\"7312345678901236999\",\"status\":\"OPEN\"}");
        assertEquals(502, send(open).statusCode());

        resetBetweenCalls();
        var create = row(BOUNDARY, "R02");
        ledger.reply("POST", ownerPath(create), 201, "{\"financeEntityId\":null}");
        assertEquals(502, send(create).statusCode());

        resetBetweenCalls();
        var periods = row(BOUNDARY, "R05");
        var foreign = (ObjectNode) periods.path("response").deepCopy();
        ((ObjectNode) foreign.path("items").get(0)).put("fiscalCalendarId", "7312345678901235999");
        ledger.reply("GET", ownerPath(periods), 200, JSON.writeValueAsString(foreign));
        assertEquals(502, send(periods).statusCode());

        resetBetweenCalls();
        var created = row(BOUNDARY, "L1");
        ledger.reply("POST", ownerPath(created), 201, "{\"chartAccountId\":\"0\"}");
        assertEquals(502, send(created).statusCode());

        resetBetweenCalls();
        var disable = row(BOUNDARY, "L3");
        var otherAccount = (ObjectNode) disable.path("response").deepCopy();
        otherAccount.put("chartAccountId", "7312345678901238999");
        ledger.reply("POST", ownerPath(disable), 200, JSON.writeValueAsString(otherAccount));
        assertEquals(502, send(disable).statusCode());

        resetBetweenCalls();
        var changes = row(BOUNDARY, "L5");
        var foreignChange = (ObjectNode) changes.path("response").deepCopy();
        ((ObjectNode) foreignChange.path("items").get(1)).put("chartAccountId", "7312345678901238999");
        ledger.reply("GET", ownerPath(changes), 200, JSON.writeValueAsString(foreignChange));
        assertEquals(502, send(changes).statusCode());
    }

    /** ledger 读取超时映射 504 DEPENDENCY_TIMEOUT，不外露地址。 */
    @Test
    void test_ownerTimeout() throws Exception {
        var row = row(BOUNDARY, "R14");
        ledger.hang("GET", ownerPath(row));

        var response = send(row);

        assertEquals(504, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(OperationsBffErrorCode.DEPENDENCY_TIMEOUT.getCode(), body.path("code").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()),
            () -> assertFalse(response.body().contains("127.0.0.1")));
    }
}
