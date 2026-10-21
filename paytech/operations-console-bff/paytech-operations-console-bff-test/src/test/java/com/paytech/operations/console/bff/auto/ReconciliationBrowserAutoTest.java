package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RECON_APPROVE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RECON_OPERATE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RECON_READ_PERMISSION_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.operations.console.bff.ext.rest.ReconciliationBankController;
import com.paytech.operations.console.bff.ext.rest.ReconciliationCashController;
import com.paytech.operations.console.bff.ext.rest.ReconciliationClearingController;
import com.paytech.operations.console.bff.ext.rest.ReconciliationExceptionController;
import com.paytech.operations.console.bff.ext.rest.ReconciliationStatementController;
import com.paytech.operations.console.bff.test.base.FinanceBrowserAutoTestBase;
import com.paytech.operations.console.bff.test.simulator.ChannelConnectorOwnerSimulator;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconLayer;
import com.paytech.reconciliation.api.enums.ReconciliationErrorCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 与 HttpExchange 转发对账的 38 个端点（R01—R38，块 123）；reconciliation 与 channel-connector 由 Loopback Simulator 承担
 * <li>36 个端点简单转发；清算账户详情（R03）补 channel-connector MID 外部编码，异常详情（R34）补所属账户标识。</li>
 * <li>BFF 不逐接口校验权限点：分权由 PLT-I02 按 Route Code 绑定的权限判定（AC-14），提交人不能复核由 reconciliation 判断。</li>
 *
 * @see ReconciliationClearingController
 * @see ReconciliationCashController
 * @see ReconciliationStatementController
 * @see ReconciliationBankController
 * @see ReconciliationExceptionController
 * @author Claude
 */
@ExtendWith(OutputCaptureExtension.class)
class ReconciliationBrowserAutoTest extends FinanceBrowserAutoTestBase {
    private static final String BOUNDARY = "/finance-reconciliation-boundary.json";
    private static final Set<String> AGGREGATED = Set.of("R03", "R34");
    private static final String MID_PATH = "/channel-connector/v1/mids/";

    @Autowired
    private ChannelConnectorOwnerSimulator channelConnector;

    static Stream<JsonNode> endpoints() {
        return rows(BOUNDARY);
    }

    static Stream<JsonNode> forwardedEndpoints() {
        return rows(BOUNDARY).filter(row -> !AGGREGATED.contains(row.path("id").asString()));
    }

    /** 本类额外使用 channel-connector：从空配置开始。 */
    @BeforeEach
    void setUpReconciliationMocks() {
        channelConnector.reset();
    }

    /** channel-connector 不得收到未配置的请求。 */
    @AfterEach
    void verifyChannelConnectorCalls() {
        try {
            assertTrue(channelConnector.unexpectedRequests().isEmpty(), channelConnector.unexpectedRequests().toString());
        } finally {
            channelConnector.reset();
        }
    }

    /** 方法、路径、Query、命令正文与操作人 Header 准确转发，状态码与响应原样返回。 */
    @ParameterizedTest(name = "{index} 对账转发")
    @MethodSource("forwardedEndpoints")
    void test_normalFlow(JsonNode row) throws Exception {
        replyAsOwner(reconciliation, row);

        assertForwarded(reconciliation, row, send(row));
    }

    /** 当前会话授权失败时不访问 reconciliation。 */
    @ParameterizedTest(name = "{index} 授权拒绝")
    @MethodSource("endpoints")
    void test_authorizationDenied(JsonNode row) throws Exception {
        platform.allow(false);

        assertDenied(reconciliation, send(row));
    }

    /** 操作与审批分属两个权限点：只有 READ + OPERATE 不能复核，只有 READ + APPROVE 不能提交，只有 READ 只能查询。 */
    @ParameterizedTest
    @CsvSource({"R13,OPERATE", "R37,OPERATE", "R36,APPROVE", "R11,APPROVE", "R18,READ", "R32,READ"})
    void test_separatedPermissions(String id, String granted) throws Exception {
        platform.grantPermissions(RECON_READ_PERMISSION_ID, switch (granted) {
            case "OPERATE" -> RECON_OPERATE_PERMISSION_ID;
            case "APPROVE" -> RECON_APPROVE_PERMISSION_ID;
            default -> RECON_READ_PERMISSION_ID;
        });

        assertDenied(reconciliation, send(row(BOUNDARY, id)));
    }

    /** 只有 READ + APPROVE 的复核人可以复核处置。 */
    @Test
    void test_approverCanReview() throws Exception {
        platform.grantPermissions(RECON_READ_PERMISSION_ID, RECON_APPROVE_PERMISSION_ID);
        var approve = row(BOUNDARY, "R37");
        replyAsOwner(reconciliation, approve);

        assertForwarded(reconciliation, approve, send(approve));
    }

    /** 清算账户详情：按挂入 MID 读取 channel-connector 详情，补外部编码与状态；其余字段原样。 */
    @Test
    void test_clearingAccountDetailAggregatesMidCodes() throws Exception {
        var row = row(BOUNDARY, "R03");
        var owner = (ObjectNode) row.path("response").deepCopy();
        var mids = (ArrayNode) owner.path("mids");
        mids.add(((ObjectNode) mids.get(0).deepCopy()).put("midId", "7400000000000000302"));
        reconciliation.reply("GET", ownerPath(row), 200, JSON.writeValueAsString(owner));
        channelConnector.reply("GET", MID_PATH + "7400000000000000301", 200, mid("7400000000000000301", "NI-MID-0001", "ACTIVE"));
        channelConnector.reply("GET", MID_PATH + "7400000000000000302", 200, mid("7400000000000000302", "NI-MID-0002", "SUSPENDED"));

        var response = send(row);

        assertEquals(200, response.statusCode(), response.body());
        var expected = owner.deepCopy();
        ((ObjectNode) expected.path("mids").get(0)).put("externalMidCode", "NI-MID-0001").put("midStatus", "ACTIVE");
        ((ObjectNode) expected.path("mids").get(1)).put("externalMidCode", "NI-MID-0002").put("midStatus", "SUSPENDED");
        assertEquals(expected, JSON.readTree(response.body()));
        assertEquals(1, reconciliation.requests().size());
        assertEquals(2, channelConnector.requests().size());
    }

    /** 清算账户没有 MID 时不访问 channel-connector；MID 详情失败时整页失败。 */
    @Test
    void test_clearingAccountDetailEdgeCase() throws Exception {
        var row = row(BOUNDARY, "R03");
        var empty = ((ObjectNode) row.path("response").deepCopy()).set("mids", JSON.createArrayNode());
        reconciliation.reply("GET", ownerPath(row), 200, JSON.writeValueAsString(empty));
        var response = send(row);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(empty, JSON.readTree(response.body()));
        assertTrue(channelConnector.requests().isEmpty());

        resetBetweenCalls();
        replyAsOwner(reconciliation, row);
        channelConnector.problem("GET", MID_PATH + "7400000000000000301", 503, CommonErrorCode.SERVICE_UNAVAILABLE.getCode());
        assertEquals(503, send(row).statusCode());
    }

    /** 银行层异常详情补银行账户名称与末四位；交易层补清算账户编码与渠道。 */
    @Test
    void test_exceptionDetailAddsAccountLabel() throws Exception {
        var row = row(BOUNDARY, "R34");
        var cash = row(BOUNDARY, "R10");
        replyAsOwner(reconciliation, row);
        replyAsOwner(reconciliation, cash);
        var response = send(row);
        assertEquals(200, response.statusCode(), response.body());
        var body = (ObjectNode) JSON.readTree(response.body());
        var account = body.remove("account");
        assertEquals(row.path("response"), body);
        assertEquals(JSON.readTree("{\"clearingAccountCode\":null,\"channelCode\":null,"
            + "\"cashAccountName\":\"AE001 USD 结算户\",\"accountLast4\":\"4321\"}"), account);

        resetBetweenCalls();
        var transaction = (ObjectNode) row.path("response").deepCopy();
        ((ObjectNode) transaction.path("exception")).put("reconLayer", ReconLayer.TRANSACTION.getCode())
            .put("sourceType", ReconExceptionSourceType.PLATFORM_TXN.getCode())
            .put("clearingAccountId", "7500000000000000101").putNull("cashAccountId");
        reconciliation.reply("GET", ownerPath(row), 200, JSON.writeValueAsString(transaction));
        replyAsOwner(reconciliation, row(BOUNDARY, "R03"));
        var transactionResponse = (ObjectNode) JSON.readTree(send(row).body());
        assertEquals("NI-AE001-USD", transactionResponse.path("account").path("clearingAccountCode").asString());
        assertEquals("NI", transactionResponse.path("account").path("channelCode").asString());
        assertTrue(channelConnector.requests().isEmpty(), "异常详情只读清算账户，不补 MID");
    }

    /** reconciliation 的业务拒绝原因（Problem detail）原样到达浏览器（AC-V6），不外露 Owner Request ID。 */
    @Test
    void test_businessRuleViolation() throws Exception {
        var approve = row(BOUNDARY, "R37");
        String detail = "提交人不能复核自己提交的处置";
        reconciliation.reply("POST", ownerPath(approve), 400, "application/problem+json",
            "{\"type\":\"about:blank\",\"title\":\"Bad Request\",\"detail\":\"" + detail + "\",\"code\":\""
                + CommonErrorCode.BUSINESS_CHECK_FAILED.getCode() + "\",\"requestId\":\"owner-request\","
                + "\"timestamp\":\"2026-10-20T00:00:00.000000Z\"}", Map.of("X-Request-Id", "owner-request"));

        var response = send(approve);

        assertEquals(400, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), body.path("code").asString()),
            () -> assertEquals(detail, body.path("detail").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()),
            () -> assertFalse(response.body().contains("owner-request")));
    }

    /** 未知 Query、请求体带操作人、非法路径变量、超过 100 行与无 Body 命令带 Body 均在访问 reconciliation 前拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        var list = row(BOUNDARY, "R33");
        assertRejectedLocally(400, reconciliation, send(list, list.path("query").asString() + "&makerId=1",
            list.path("request"), true));

        resetBetweenCalls();
        var submit = row(BOUNDARY, "R36");
        var maker = ((ObjectNode) submit.path("request").deepCopy()).put("makerId", "10001");
        assertRejectedLocally(400, reconciliation, send(submit, "", maker, true));

        resetBetweenCalls();
        var detail = ((ObjectNode) row(BOUNDARY, "R15").deepCopy()).put("path", "/finance/reconciliation/v1/channel-statements/0");
        assertRejectedLocally(400, reconciliation, send(detail, "", detail.path("request"), true));

        resetBetweenCalls();
        var append = row(BOUNDARY, "R18");
        assertRejectedLocally(422, reconciliation, send(append, "", channelLines(101, 0), true));

        resetBetweenCalls();
        var complete = row(BOUNDARY, "R19");
        assertRejectedLocally(400, reconciliation, send(complete, "", JSON.readTree("{}"), true));

        resetBetweenCalls();
        var search = row(BOUNDARY, "R39");
        assertRejectedLocally(400, reconciliation, send(search, "?statementId=1", search.path("request"), true));

        resetBetweenCalls();
        assertRejectedLocally(400, reconciliation, send(search, "?referenceType=ni", search.path("request"), true));

        resetBetweenCalls();
        var summary = row(BOUNDARY, "R38");
        assertRejectedLocally(400, reconciliation, send(summary, "?entityNo=AE001", summary.path("request"), true));
    }

    /** 一段 100 行的追加请求可超过 8192 字节并完整转发；带大量渠道特有列超过 65536 字节时 413，不访问 reconciliation。 */
    @Test
    void test_largeLineChunk() throws Exception {
        var append = row(BOUNDARY, "R18");
        var lines = channelLines(100, 0);
        assertTrue(JSON.writeValueAsString(lines).length() > 8192);
        replyAsOwner(reconciliation, append);
        var response = send(append, "", lines, true);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(lines, JSON.readTree(reconciliation.requests().getFirst().body()));

        resetBetweenCalls();
        assertRejectedLocally(413, reconciliation, send(append, "", channelLines(100, 2), true));
    }

    /** 银行账户新建的账号明文不进入 BFF 日志（I-05）：请求与响应只记元数据。 */
    @Test
    void test_cashAccountNumberNotLogged(CapturedOutput output) throws Exception {
        var create = row(BOUNDARY, "R11");
        replyAsOwner(reconciliation, create);

        assertForwarded(reconciliation, create, send(create));

        String accountNumber = create.path("request").path("accountNumber").asString();
        assertFalse(output.getAll().contains(accountNumber), "银行账号明文不得出现在日志中");
    }

    /** 详情主键与请求不一致、列表元素不属于请求对象、多选匹配回执条数不符、开始导入回执缺主键时视为上游无效响应 502。 */
    @Test
    void test_invalidUpstreamResponse() throws Exception {
        var detail = row(BOUNDARY, "R15");
        var other = (ObjectNode) detail.path("response").deepCopy();
        ((ObjectNode) other.path("statement")).put("statementId", "7500000000000000399");
        reconciliation.reply("GET", ownerPath(detail), 200, JSON.writeValueAsString(other));
        assertEquals(502, send(detail).statusCode());

        resetBetweenCalls();
        var lines = row(BOUNDARY, "R23");
        var foreign = (ObjectNode) lines.path("response").deepCopy();
        ((ObjectNode) foreign.path("items").get(0)).put("bankStatementId", "7500000000000000699");
        reconciliation.reply("GET", ownerPath(lines), 200, JSON.writeValueAsString(foreign));
        assertEquals(502, send(lines).statusCode());

        resetBetweenCalls();
        var create = row(BOUNDARY, "R32");
        reconciliation.reply("POST", ownerPath(create), 201, "{\"items\":[]}");
        assertEquals(502, send(create).statusCode());

        resetBetweenCalls();
        var start = row(BOUNDARY, "R17");
        var missing = ((ObjectNode) start.path("response").deepCopy()).putNull("statementId");
        reconciliation.reply("POST", ownerPath(start), 201, JSON.writeValueAsString(missing));
        assertEquals(502, send(start).statusCode());
    }

    /** 详情页对象不存在：reconciliation 的 404 合同错误码原样到达浏览器为 404。 */
    @ParameterizedTest
    @CsvSource({"R40,BANK_STATEMENT_NOT_FOUND", "R41,BANK_STATEMENT_LINE_NOT_FOUND"})
    void test_noDataFound(String id, ReconciliationErrorCode code) throws Exception {
        var detail = row(BOUNDARY, id);
        reconciliation.reply("GET", ownerPath(detail), 404, "application/problem+json",
            "{\"type\":\"about:blank\",\"title\":\"Not Found\",\"detail\":\"不存在\",\"code\":\"" + code.getCode()
                + "\",\"requestId\":\"owner-request\",\"timestamp\":\"2026-10-21T00:00:00.000000Z\"}",
            Map.of("X-Request-Id", "owner-request"));

        var response = send(detail);

        assertEquals(404, response.statusCode(), response.body());
        assertEquals(code.getCode(), JSON.readTree(response.body()).path("code").asString());
    }

    /** 渠道流水与银行流水详情的上游回执不一致时 502：元素不属于请求清算账户、匹配不属于请求流水、匹配与预期流水不对应、对账单不是流水所属。 */
    @Test
    void test_detailInvalidUpstreamResponse() throws Exception {
        var search = row(BOUNDARY, "R39");
        var foreign = (ObjectNode) search.path("response").deepCopy();
        ((ObjectNode) foreign.path("items").get(0)).put("clearingAccountId", "7500000000000000199");
        reconciliation.reply("GET", ownerPath(search), 200, JSON.writeValueAsString(foreign));
        assertEquals(502, send(search).statusCode());

        var detail = row(BOUNDARY, "R41");
        for (String broken : List.of("match.bankStatementLineId", "match.expectedMovementId", "statement.bankStatementId")) {
            resetBetweenCalls();
            var body = (ObjectNode) detail.path("response").deepCopy();
            if (broken.equals("statement.bankStatementId")) {
                ((ObjectNode) body.path("statement")).put("bankStatementId", "7500000000000000699");
            } else {
                ((ObjectNode) body.path("matches").get(0).path("match")).put(broken.substring(6), "7500000000000000999");
            }
            reconciliation.reply("GET", ownerPath(detail), 200, JSON.writeValueAsString(body));
            assertEquals(502, send(detail).statusCode(), broken);
        }

        resetBetweenCalls();
        var missing = ((ObjectNode) detail.path("response").deepCopy()).putNull("openExceptions");
        reconciliation.reply("GET", ownerPath(detail), 200, JSON.writeValueAsString(missing));
        assertEquals(502, send(detail).statusCode());
    }

    /** channel-connector MID 详情 DTO。 */
    private static String mid(String midId, String externalMidCode, String status) {
        return "{\"midId\":\"" + midId + "\",\"channelId\":\"7400000000000000101\",\"channelAccountId\":\"7400000000000000201\","
            + "\"externalMidCode\":\"" + externalMidCode + "\",\"entityNo\":\"AE001\",\"status\":\"" + status + "\","
            + "\"activatedAt\":\"2026-10-20T02:00:00Z\",\"closedAt\":null,\"createTime\":\"2026-10-20T01:00:00Z\","
            + "\"updateTime\":\"2026-10-20T02:00:00Z\",\"capabilities\":[]}";
    }

    /** 指定行数的渠道账单行；每行带 extraColumns 个约 1 KiB 的渠道特有列。 */
    private static ObjectNode channelLines(int count, int extraColumns) {
        var body = JSON.createObjectNode();
        var lines = body.putArray("lines");
        for (int i = 1; i <= count; i++) {
            var line = lines.addObject().put("lineNo", i).put("lineType", "PAYMENT").put("direction", "CREDIT")
                .put("amount", "100.00").put("feeAmount", "0.45").put("referenceType", "NI_ORDER_REF")
                .put("externalTransactionId", "NI-ORD-" + i).put("transactionDate", "2026-10-18");
            if (extraColumns == 0) {
                line.putNull("rawAttributes");
                continue;
            }
            var raw = line.putObject("rawAttributes");
            for (int c = 0; c < extraColumns; c++) {
                raw.put("column" + c, "v".repeat(c == 0 ? 40 : 1000));
            }
        }
        return body;
    }

    /** 边界清单 41 条路由码唯一且都属于对账路由前缀，与注册块 123（38 条）、块 126（3 条）的路由一一对应。 */
    @Test
    void test_boundaryMatchesRegisteredRoutes() {
        List<String> codes = rows(BOUNDARY).map(row -> row.path("routeCode").asString()).toList();
        assertEquals(41, codes.size());
        assertEquals(41, Set.copyOf(codes).size());
        assertNull(codes.stream().filter(code -> !code.startsWith("operations-finance-recon-")).findFirst().orElse(null));
    }
}
