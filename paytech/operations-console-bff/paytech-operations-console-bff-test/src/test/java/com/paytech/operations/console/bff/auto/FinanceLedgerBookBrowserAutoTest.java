package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.FINANCE_READ_PERMISSION_ID;
import static com.paytech.operations.console.bff.service.model.FinanceLedgerBookModels.LEDGER_OWNER_TYPE_PATTERN;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.account.api.enums.OwnerType;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.operations.console.bff.ext.rest.FinanceLedgerBookController;
import com.paytech.operations.console.bff.test.base.FinanceBrowserAutoTestBase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 与 HttpExchange 转发账簿、账户、流水、分录详情、科目余额与试算记录的 9 个端点（R08—R13、L6、T1、T2）；ledger 由 Loopback Simulator 承担
 *
 * @see FinanceLedgerBookController
 * @author Claude
 */
class FinanceLedgerBookBrowserAutoTest extends FinanceBrowserAutoTestBase {
    private static final String BOUNDARY = "/finance-ledger-boundary.json";

    static Stream<JsonNode> endpoints() {
        return rows(BOUNDARY);
    }

    /** 方法、路径、Query、命令正文与操作人 Header 准确转发，状态码与响应原样返回（含流水与分录中的空来源字段）。 */
    @ParameterizedTest(name = "{index} 账簿与流水转发")
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

    /** ACCOUNT_POSTING 来源的流水行与分录详情透传 account 批次主键、序号与行级流水主键；人工行这些字段为空。 */
    @Test
    void test_accountPostingOriginForwarded() throws Exception {
        var lines = row(BOUNDARY, "R12");
        replyAsOwner(ledger, lines);
        var items = JSON.readTree(send(lines).body()).path("items");
        assertAll(() -> assertEquals("ACCOUNT_POSTING", items.path(0).path("originType").asString()),
            () -> assertEquals("7318802201114002018", items.path(0).path("accountPostingId").asString()),
            () -> assertEquals("18", items.path(0).path("accountPostingSequence").asString()),
            () -> assertTrue(items.path(1).path("accountPostingId").isNull()),
            () -> assertTrue(items.path(1).path("accountPostingSequence").isNull()));

        resetBetweenCalls();
        var entry = row(BOUNDARY, "R13");
        replyAsOwner(ledger, entry);
        var detail = JSON.readTree(send(entry).body());
        assertAll(() -> assertEquals("ACCOUNT_POSTING", detail.path("originType").asString()),
            () -> assertEquals("7318802201114002018", detail.path("accountPostingId").asString()),
            () -> assertEquals("18", detail.path("accountPostingSequence").asString()),
            () -> assertTrue(detail.path("lines").path(0).path("accountEntryId").isNull()),
            () -> assertEquals("7400000000000001", detail.path("lines").path(1).path("accountEntryId").asString()));
    }

    /** 科目余额与试算详情透传科目编号、上级、级次与汇总行；账户与分录行透传账户号与科目名称。 */
    @Test
    void test_chartHierarchyFieldsForwarded() throws Exception {
        var balances = row(BOUNDARY, "L6");
        replyAsOwner(ledger, balances);
        var lines = JSON.readTree(send(balances).body()).path("lines");
        assertAll(() -> assertEquals("2201", lines.path(0).path("accountNo").asString()),
            () -> assertTrue(lines.path(0).path("parentChartAccountId").isNull()),
            () -> assertFalse(lines.path(0).path("postingAllowed").asBoolean()),
            () -> assertEquals(lines.path(0).path("chartAccountId"), lines.path(1).path("parentChartAccountId")),
            () -> assertEquals(2, lines.path(1).path("level").asInt()),
            () -> assertEquals(lines.path(1).path("chartAccountId"), lines.path(2).path("parentChartAccountId")),
            () -> assertEquals(3, lines.path(2).path("level").asInt()));

        resetBetweenCalls();
        var trial = row(BOUNDARY, "T2");
        replyAsOwner(ledger, trial);
        var detail = JSON.readTree(send(trial).body());
        assertAll(() -> assertEquals(2, detail.path("lines").size()),
            () -> assertEquals(4, detail.path("summaryLines").size()),
            () -> assertFalse(detail.path("summaryLines").path(0).path("postingAllowed").asBoolean()),
            () -> assertEquals(1, detail.path("exceptions").size()));

        resetBetweenCalls();
        var entry = row(BOUNDARY, "R13");
        replyAsOwner(ledger, entry);
        var entryLines = JSON.readTree(send(entry).body()).path("lines");
        assertAll(() -> assertEquals("AE001-USD-22010101-0001", entryLines.path(1).path("accountNo").asString()),
            () -> assertEquals("Client 应付款", entryLines.path(1).path("accountName").asString()));
    }

    /** 执行试算不经浏览器接入：POST 试算路径不访问 ledger。 */
    @Test
    void test_trialBalanceRunNotExposed() throws Exception {
        var list = (ObjectNode) row(BOUNDARY, "T1").deepCopy();
        list.put("method", "POST");

        var response = send(list, "", JSON.nullNode(), true);

        assertTrue(response.statusCode() >= 400 && response.statusCode() < 500, response.body());
        assertTrue(ledger.requests().isEmpty(), ledger.requests().toString());
    }

    /** 账户核算对象类型校验与 ledger 合同 LedgerOwnerType 的全部 Code 完全一致。 */
    @Test
    void test_ownerTypePatternMatchesLedgerContract() {
        assertEquals(Arrays.stream(LedgerOwnerType.values()).map(LedgerOwnerType::getCode)
            .collect(Collectors.joining("|")), LEDGER_OWNER_TYPE_PATTERN);
    }

    /** Client 与 Local Partner 不再是 ledger 核算对象：账户筛选（Query 400）与新建账户（Body 字段 422）都在访问 ledger 前拒绝。 */
    @ParameterizedTest
    @CsvSource({"CLIENT", "LOCAL_PARTNER"})
    void test_subledgerOwnerTypeRejected(OwnerType ownerType) throws Exception {
        var accounts = row(BOUNDARY, "R10");
        assertRejectedLocally(400, ledger, send(accounts, "?ownerType=" + ownerType.getCode(),
            accounts.path("request"), true));

        resetBetweenCalls();
        var create = row(BOUNDARY, "R11");
        var body = (ObjectNode) create.path("request").deepCopy();
        body.put("ownerType", ownerType.getCode());
        assertRejectedLocally(422, ledger, send(create, "", body, true));
    }

    /** 只有 READ 权限的操作人不能新建账簿或账户。 */
    @ParameterizedTest
    @CsvSource({"R09", "R11"})
    void test_readOnlyOperatorCannotManage(String id) throws Exception {
        platform.grantPermissions(FINANCE_READ_PERMISSION_ID);

        assertDenied(ledger, send(row(BOUNDARY, id)));
    }

    /** ledger 登记的业务拒绝以 400 与枚举 Code 原样到达浏览器。 */
    @ParameterizedTest
    @CsvSource({"R09,LEDGER_BOOK_ALREADY_EXISTS", "R09,CURRENCY_NOT_ACTIVE", "R09,CHART_NOT_ACTIVE",
        "R09,FINANCE_ENTITY_UNAVAILABLE", "R11,LEDGER_BOOK_NOT_FOUND", "R11,CHART_ACCOUNT_NOT_POSTABLE",
        "R11,ACCOUNT_IDENTITY_INVALID", "R11,ACCOUNT_ALREADY_EXISTS", "R10,LEDGER_BOOK_NOT_FOUND",
        "R13,LEDGER_BOOK_NOT_FOUND", "R11,CHART_ACCOUNT_INACTIVE"})
    void test_businessRuleViolation(String id, LedgerSetupErrorCode code) throws Exception {
        var row = row(BOUNDARY, id);
        ledger.problem(row.path("method").asString(), ownerPath(row), 400, code.getCode());

        assertCodedRejection(code, send(row));
    }

    /** 非法分页、未知 Query、查询带 Body、非法路径变量与日期格式、未知 Body 字段在访问 ledger 前拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        var lines = row(BOUNDARY, "R12");
        assertRejectedLocally(400, ledger, send(lines, "?page=1&size=7", lines.path("request"), true));

        resetBetweenCalls();
        assertRejectedLocally(400, ledger, send(lines, "?postingDateFrom=2026/10/01", lines.path("request"), true));

        resetBetweenCalls();
        var accounts = row(BOUNDARY, "R10");
        assertRejectedLocally(400, ledger, send(accounts, accounts.path("query").asString() + "&normalSide=CREDIT",
            accounts.path("request"), true));

        resetBetweenCalls();
        var books = row(BOUNDARY, "R08");
        assertRejectedLocally(400, ledger, send(books, "", JSON.createObjectNode(), true));

        resetBetweenCalls();
        var entry = (ObjectNode) row(BOUNDARY, "R13").deepCopy();
        entry.put("path", "/finance/ledger-books/v1/7312345678901239001/journal-entries/not-an-id");
        assertRejectedLocally(400, ledger, send(entry, "", entry.path("request"), true));

        resetBetweenCalls();
        var create = row(BOUNDARY, "R11");
        var unknown = (ObjectNode) create.path("request").deepCopy();
        unknown.put("normalSide", "CREDIT");
        assertRejectedLocally(400, ledger, send(create, "", unknown, true));

        resetBetweenCalls();
        assertRejectedLocally(400, ledger, send(accounts, "?accountNo=" + "A".repeat(49), accounts.path("request"), true));

        resetBetweenCalls();
        var balances = row(BOUNDARY, "L6");
        assertRejectedLocally(400, ledger, send(balances, "?page=1", balances.path("request"), true));

        resetBetweenCalls();
        var trial = (ObjectNode) row(BOUNDARY, "T2").deepCopy();
        trial.put("path", trial.path("path").asString().replaceFirst("/2$", "/0"));
        assertRejectedLocally(400, ledger, send(trial, "", trial.path("request"), true));
    }

    /** 分录或账簿主键与请求不一致、分页总数不自洽时视为上游无效响应 502。 */
    @Test
    void test_invalidUpstreamResponse() throws Exception {
        var entry = row(BOUNDARY, "R13");
        var other = (ObjectNode) entry.path("response").deepCopy();
        other.put("ledgerBookId", "7312345678901239999");
        ledger.reply("GET", ownerPath(entry), 200, JSON.writeValueAsString(other));
        assertEquals(502, send(entry).statusCode());

        resetBetweenCalls();
        var accounts = row(BOUNDARY, "R10");
        var page = (ObjectNode) accounts.path("response").deepCopy();
        page.put("total", 5);
        ledger.reply("GET", ownerPath(accounts), 200, JSON.writeValueAsString(page));
        assertEquals(502, send(accounts).statusCode());

        resetBetweenCalls();
        var create = row(BOUNDARY, "R09");
        ledger.reply("POST", ownerPath(create), 201, "{\"ledgerBookId\":\"0\"}");
        assertEquals(502, send(create).statusCode());

        resetBetweenCalls();
        var balances = row(BOUNDARY, "L6");
        var otherPeriod = (ObjectNode) balances.path("response").deepCopy();
        otherPeriod.put("periodId", "7312345678901236999");
        ledger.reply("GET", ownerPath(balances), 200, JSON.writeValueAsString(otherPeriod));
        assertEquals(502, send(balances).statusCode());

        resetBetweenCalls();
        var trial = row(BOUNDARY, "T2");
        var otherRun = (ObjectNode) trial.path("response").deepCopy();
        otherRun.put("runNo", "3");
        ledger.reply("GET", ownerPath(trial), 200, JSON.writeValueAsString(otherRun));
        assertEquals(502, send(trial).statusCode());

        resetBetweenCalls();
        var noSummary = (ObjectNode) trial.path("response").deepCopy();
        noSummary.putNull("summaryLines");
        ledger.reply("GET", ownerPath(trial), 200, JSON.writeValueAsString(noSummary));
        assertEquals(502, send(trial).statusCode());

        resetBetweenCalls();
        var runs = row(BOUNDARY, "T1");
        var runsPage = (ObjectNode) runs.path("response").deepCopy();
        runsPage.put("size", 20);
        ledger.reply("GET", ownerPath(runs), 200, JSON.writeValueAsString(runsPage));
        assertEquals(502, send(runs).statusCode());
    }
}
