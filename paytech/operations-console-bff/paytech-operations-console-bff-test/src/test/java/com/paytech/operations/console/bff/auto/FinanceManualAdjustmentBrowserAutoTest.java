package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.FINANCE_APPROVE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.FINANCE_MANAGE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.FINANCE_READ_PERMISSION_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.ledger.api.enums.LedgerErrorCode;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.operations.console.bff.ext.rest.FinanceManualAdjustmentController;
import com.paytech.operations.console.bff.test.base.FinanceBrowserAutoTestBase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 与 HttpExchange 转发人工调整的 5 个端点（M1—M5：列表、提交、详情、批准、驳回）；ledger 由 Loopback Simulator 承担
 *
 * @see FinanceManualAdjustmentController
 * @author Claude
 */
class FinanceManualAdjustmentBrowserAutoTest extends FinanceBrowserAutoTestBase {
    private static final String BOUNDARY = "/finance-adjustment-boundary.json";

    static Stream<JsonNode> endpoints() {
        return rows(BOUNDARY).filter(row -> row.path("id").asString().startsWith("M"));
    }

    /** 方法、路径、Query、命令正文与操作人 Header 准确转发，状态码与响应原样返回（含未过账的空分录字段）。 */
    @ParameterizedTest(name = "{index} 人工调整转发")
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

    /** 制单与复核分属 MANAGE 与 APPROVE：只有 READ + MANAGE 不能复核，只有 READ + APPROVE 不能提交，只有 READ 只能查询。 */
    @ParameterizedTest
    @CsvSource({"M4,MANAGE", "M5,MANAGE", "M2,APPROVE", "M2,READ"})
    void test_separatedPermissions(String id, String granted) throws Exception {
        platform.grantPermissions(FINANCE_READ_PERMISSION_ID, switch (granted) {
            case "MANAGE" -> FINANCE_MANAGE_PERMISSION_ID;
            case "APPROVE" -> FINANCE_APPROVE_PERMISSION_ID;
            default -> FINANCE_READ_PERMISSION_ID;
        });

        assertDenied(ledger, send(row(BOUNDARY, id)));
    }

    /** 只有 READ + APPROVE 的复核人可以批准，编制人 Header 换成复核人会话。 */
    @Test
    void test_approverCanReview() throws Exception {
        platform.grantPermissions(FINANCE_READ_PERMISSION_ID, FINANCE_APPROVE_PERMISSION_ID);
        var approve = row(BOUNDARY, "M4");
        replyAsOwner(ledger, approve);

        assertForwarded(ledger, approve, send(approve));
    }

    /** 提交与复核的 LedgerErrorCode 拒绝（D4）Code 与默认消息原样到达，不外露 ledger 原文。 */
    @ParameterizedTest(name = "{index} {0} {1}")
    @CsvSource({"M2,ADJUSTMENT_UNBALANCED", "M2,ADJUSTMENT_PERIOD_NOT_OPEN", "M2,ADJUSTMENT_ACCOUNT_INVALID",
        "M2,OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN", "M4,SELF_REVIEW_FORBIDDEN", "M4,REVIEW_CONTENT_CHANGED",
        "M4,REVIEW_STATUS_INVALID", "M5,SELF_REVIEW_FORBIDDEN", "M5,REVIEW_STATUS_INVALID"})
    void test_businessRuleViolation(String id, LedgerErrorCode code) throws Exception {
        var row = row(BOUNDARY, id);
        ledger.problem("POST", ownerPath(row), 400, code.getCode());

        assertCodedRejection(code, send(row));
    }

    /** 账簿不存在沿用 LedgerSetupErrorCode；未登记的业务拒绝保留公共 Code，不外露 ledger 原文。 */
    @Test
    void test_noDataFoundAndCommonBusinessCheckFailed() throws Exception {
        var list = row(BOUNDARY, "M1");
        ledger.problem("GET", ownerPath(list), 400, LedgerSetupErrorCode.LEDGER_BOOK_NOT_FOUND.getCode());
        assertCodedRejection(LedgerSetupErrorCode.LEDGER_BOOK_NOT_FOUND, send(list));

        resetBetweenCalls();
        var approve = row(BOUNDARY, "M4");
        ledger.problem("POST", ownerPath(approve), 400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode());
        assertCodedRejection(CommonErrorCode.BUSINESS_CHECK_FAILED, send(approve));
    }

    /** 缺少账簿、未知 Query、请求体带 makerId、明细不足两行、非法摘要与路径变量均在访问 ledger 前拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        var list = row(BOUNDARY, "M1");
        assertRejectedLocally(400, ledger, send(list, "?page=1&size=10", list.path("request"), true));

        resetBetweenCalls();
        assertRejectedLocally(400, ledger, send(list, list.path("query").asString() + "&currency=USD",
            list.path("request"), true));

        resetBetweenCalls();
        assertRejectedLocally(400, ledger, send(list, list.path("query").asString().replace("POSTED", "DRAFT"),
            list.path("request"), true));

        resetBetweenCalls();
        var submit = row(BOUNDARY, "M2");
        var maker = (ObjectNode) submit.path("request").deepCopy();
        maker.put("makerId", "10001");
        assertRejectedLocally(400, ledger, send(submit, "", maker, true));

        resetBetweenCalls();
        var oneLine = (ObjectNode) submit.path("request").deepCopy();
        ((ArrayNode) oneLine.path("lines")).remove(1);
        assertRejectedLocally(422, ledger, send(submit, "", oneLine, true));

        resetBetweenCalls();
        var approve = row(BOUNDARY, "M4");
        assertRejectedLocally(422, ledger, send(approve, "", JSON.readTree("{\"approvedContentHash\":\"ABC\"}"), true));

        resetBetweenCalls();
        var detail = (ObjectNode) row(BOUNDARY, "M3").deepCopy();
        detail.put("path", "/finance/manual-adjustments/v1/0");
        assertRejectedLocally(400, ledger, send(detail, "", detail.path("request"), true));
    }

    /** 明细较多时请求体可超过 8192 字节并完整转发；超过 65536 字节时 413，不访问 ledger。 */
    @Test
    void test_largeLineSet() throws Exception {
        var submit = row(BOUNDARY, "M2");
        var many = withLines(submit.path("request"), 120);
        replyAsOwner(ledger, submit);
        var response = send(submit, "", many, true);
        assertEquals(201, response.statusCode(), response.body());
        assertEquals(many, JSON.readTree(ledger.requests().getFirst().body()));

        resetBetweenCalls();
        assertRejectedLocally(413, ledger, send(submit, "", withLines(submit.path("request"), 500), true));
    }

    /** 列表元素不属于请求账簿、详情或复核回执主键不一致、提交回执缺主键时视为上游无效响应 502。 */
    @Test
    void test_invalidUpstreamResponse() throws Exception {
        var list = row(BOUNDARY, "M1");
        var foreign = (ObjectNode) list.path("response").deepCopy();
        ((ObjectNode) foreign.path("items").get(0)).put("ledgerBookId", "7312345678901239999");
        ledger.reply("GET", ownerPath(list), 200, JSON.writeValueAsString(foreign));
        assertEquals(502, send(list).statusCode());

        resetBetweenCalls();
        var detail = row(BOUNDARY, "M3");
        var other = (ObjectNode) detail.path("response").deepCopy();
        other.put("manualAdjustmentId", "7312345678901250999");
        ledger.reply("GET", ownerPath(detail), 200, JSON.writeValueAsString(other));
        assertEquals(502, send(detail).statusCode());

        resetBetweenCalls();
        var approve = row(BOUNDARY, "M4");
        var otherReview = (ObjectNode) approve.path("response").deepCopy();
        otherReview.put("manualAdjustmentId", "7312345678901250999");
        ledger.reply("POST", ownerPath(approve), 200, JSON.writeValueAsString(otherReview));
        assertEquals(502, send(approve).statusCode());

        resetBetweenCalls();
        var submit = row(BOUNDARY, "M2");
        ledger.reply("POST", ownerPath(submit), 201, "{\"manualAdjustmentId\":null,\"status\":\"SUBMITTED\"}");
        assertEquals(502, send(submit).statusCode());
    }

    /** 以样例首行复制出指定行数的明细。 */
    private static ObjectNode withLines(JsonNode request, int count) {
        var body = (ObjectNode) request.deepCopy();
        var lines = JSON.createArrayNode();
        for (int i = 0; i < count; i++) {
            var line = (ObjectNode) request.path("lines").get(i % 2).deepCopy();
            line.put("lineDescription", "更正手续费第 " + i + " 行：" + "x".repeat(40));
            lines.add(line);
        }
        body.set("lines", lines);
        return body;
    }
}
