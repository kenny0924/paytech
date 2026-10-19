package com.paytech.operations.console.bff.auto;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.account.api.enums.BalanceQueryErrorCode;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.ext.rest.FinanceBalanceController;
import com.paytech.operations.console.bff.test.base.FinanceBrowserAutoTestBase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 与 HttpExchange 转发 Owner 余额查询的 2 个端点（R17—R18）；account 由 Loopback Simulator 承担
 *
 * @see FinanceBalanceController
 * @author Claude
 */
class FinanceBalanceBrowserAutoTest extends FinanceBrowserAutoTestBase {
    private static final String BOUNDARY = "/finance-balance-boundary.json";

    static Stream<JsonNode> endpoints() {
        return rows(BOUNDARY);
    }

    /** 路径与 Query 准确转发，余额原样返回；查询不携带操作人 Header。 */
    @ParameterizedTest(name = "{index} 余额转发")
    @MethodSource("endpoints")
    void test_normalFlow(JsonNode row) throws Exception {
        replyAsOwner(account, row);

        assertForwarded(account, row, send(row));
    }

    /** 当前会话授权失败时不访问 account。 */
    @ParameterizedTest(name = "{index} 授权拒绝")
    @MethodSource("endpoints")
    void test_authorizationDenied(JsonNode row) throws Exception {
        platform.allow(false);

        assertDenied(account, send(row));
    }

    /** Local Partner 余额不含 Client 保证金字段。 */
    @Test
    void test_partnerBalanceHasNoSecurityDeposit() throws Exception {
        var row = row(BOUNDARY, "R18");
        replyAsOwner(account, row);

        var body = JSON.readTree(send(row).body());

        assertFalse(body.has("securityDepositAmount"));
        assertTrue(body.has("recoveryReceivableAmount"));
    }

    /** 无账簿时 account 的 LEDGER_BOOK_NOT_FOUND 以 account 合同说明返回，而不是 ledger 同名错误码的说明。 */
    @ParameterizedTest
    @CsvSource({"R17", "R18"})
    void test_noDataFound(String id) throws Exception {
        var row = row(BOUNDARY, id);
        account.problem("GET", ownerPath(row), 400, BalanceQueryErrorCode.LEDGER_BOOK_NOT_FOUND.getCode());

        var response = send(row);

        assertCodedRejection(BalanceQueryErrorCode.LEDGER_BOOK_NOT_FOUND, response);
        assertFalse(response.body().contains(LedgerSetupErrorCode.LEDGER_BOOK_NOT_FOUND.getMessage()));
    }

    /** 缺少必填 Query、非法 entityNo、未知 Query、非法 Owner ID、查询带 Body 在访问 account 前拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        var client = row(BOUNDARY, "R17");
        assertRejectedLocally(400, account, send(client, "?currency=USD", client.path("request"), true));

        resetBetweenCalls();
        assertRejectedLocally(400, account, send(client, "?entityNo=ae001&currency=USD", client.path("request"), true));

        resetBetweenCalls();
        assertRejectedLocally(400, account, send(client, client.path("query").asString() + "&page=1",
            client.path("request"), true));

        resetBetweenCalls();
        var partner = (ObjectNode) row(BOUNDARY, "R18").deepCopy();
        partner.put("path", "/finance/balances/v1/partners/P-001");
        assertRejectedLocally(400, account, send(partner, partner.path("query").asString(), partner.path("request"), true));

        resetBetweenCalls();
        assertRejectedLocally(400, account, send(client, client.path("query").asString(), JSON.createObjectNode(), true));
    }

    /** 回执的 Owner、entityNo 或币种与请求不一致时视为上游无效响应 502。 */
    @ParameterizedTest
    @CsvSource({"clientId,880000000000071999", "entityNo,AE002", "currency,AED"})
    void test_invalidUpstreamResponse(String field, String value) throws Exception {
        var row = row(BOUNDARY, "R17");
        var other = (ObjectNode) row.path("response").deepCopy();
        other.put(field, value);
        account.reply("GET", ownerPath(row), 200, JSON.writeValueAsString(other));

        assertEquals(502, send(row).statusCode());
    }

    /** account 读取超时映射 504 DEPENDENCY_TIMEOUT。 */
    @Test
    void test_ownerTimeout() throws Exception {
        var row = row(BOUNDARY, "R18");
        account.hang("GET", ownerPath(row));

        var response = send(row);

        assertEquals(504, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(OperationsBffErrorCode.DEPENDENCY_TIMEOUT.getCode(), body.path("code").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()));
    }
}
