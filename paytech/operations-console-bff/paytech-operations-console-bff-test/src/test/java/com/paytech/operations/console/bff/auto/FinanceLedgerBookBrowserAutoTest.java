package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.FINANCE_READ_PERMISSION_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.operations.console.bff.ext.rest.FinanceLedgerBookController;
import com.paytech.operations.console.bff.test.base.FinanceBrowserAutoTestBase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 与 HttpExchange 转发账簿、账户、流水与分录详情的 6 个端点（R08—R13）；ledger 由 Loopback Simulator 承担
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
        "R13,LEDGER_BOOK_NOT_FOUND"})
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
    }
}
