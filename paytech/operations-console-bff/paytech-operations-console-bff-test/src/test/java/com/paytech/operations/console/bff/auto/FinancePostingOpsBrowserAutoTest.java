package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ACCOUNT_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.AUTHORIZE_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.FINANCE_MANAGE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.FINANCE_READ_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.account.api.enums.AccountPostingErrorCode;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.api.enums.PostingProgressStatus;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.ext.rest.FinancePostingOpsController;
import com.paytech.operations.console.bff.service.processor.PostingProgressProcessor;
import com.paytech.operations.console.bff.test.base.FinanceBrowserAutoTestBase;
import com.paytech.operations.console.bff.test.simulator.FinanceOwnerSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RecordedRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 访问明细账运维的 4 个端点（PO1—PO4）；ledger 与 account 由 Loopback Simulator 承担
 * <li>PO1 账簿入账进度由 BFF 聚合：ledger 两段进度原样带出，落后的流补充 account 的"卡住的批次"，单条读取失败只让该条为空。</li>
 * <li>PO2 批次浏览转发 account 并裁剪为不含流水的摘要；PO3 追平与 PO4 重建带会话操作人，使用长耗时读取超时（测试配置 5 秒），
 * 业务拒绝的 Owner detail 原样到达浏览器。</li>
 *
 * @see FinancePostingOpsController
 * @see PostingProgressProcessor
 * @author Claude
 */
class FinancePostingOpsBrowserAutoTest extends FinanceBrowserAutoTestBase {
    private static final String BOUNDARY = "/finance-posting-ops-boundary.json";

    static Stream<JsonNode> endpoints() {
        return rows(BOUNDARY);
    }

    /** 每个端点按样例调用 Owner：方法、路径、Query 与正文一致，命令带操作人 Header，查询不带；浏览器收到样例响应。 */
    @ParameterizedTest(name = "{index} {0}")
    @MethodSource("endpoints")
    void test_normalFlow(JsonNode row) throws Exception {
        var owner = replyAsOwners(row);

        var response = send(row);

        assertEquals(row.path("status").asInt(), response.statusCode(), response.body());
        assertEquals(row.path("response"), JSON.readTree(response.body()));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(null));
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
        var call = owner.requests().getFirst();
        boolean command = !"GET".equals(row.path("method").asString());
        assertAll(() -> assertEquals(row.path("method").asString(), call.method()),
            () -> assertEquals(row.path("ownerPath").asString(), call.path()),
            () -> assertEquals(query(row.path("ownerQuery").asString()), query(call.query())),
            () -> assertEquals(REQUEST_ID, call.header("X-Request-Id")),
            () -> assertEquals(command && "ledger".equals(row.path("owner").asString()) ? ACCOUNT_ID : null,
                call.header("X-Gateway-Actor-Subject")),
            () -> assertEquals(command && "ledger".equals(row.path("owner").asString()) ? SESSION_ID : null,
                call.header("X-Gateway-Session-Id")),
            () -> assertNull(call.header("Authorization")), () -> assertNull(call.header("Cookie")),
            () -> assertNull(call.header("Idempotency-Key")));
        if (row.path("ownerRequest").isNull()) {
            assertTrue(call.body().isEmpty(), "无 Body 的请求不得转发 Body");
        } else {
            assertEquals(ownerRequest(row), JSON.readTree(call.body()));
        }
        if (row.has("stuckOwnerPath")) {
            assertEquals(1, account.requests().size(), "只有落后的流读取卡住的批次");
            assertEquals(query(row.path("stuckOwnerQuery").asString()), query(account.requests().getFirst().query()));
        }
    }

    /** 当前会话授权失败时不访问 ledger 与 account。 */
    @ParameterizedTest(name = "{index} 授权拒绝")
    @MethodSource("endpoints")
    void test_authorizationDenied(JsonNode row) throws Exception {
        platform.allow(false);

        var response = send(row);

        assertDenied(owner(row), response);
        assertTrue(ledger.requests().isEmpty() && account.requests().isEmpty());
    }

    /** 只有查看权限（FINANCE_ACCOUNTING_READ）时追平与重建被 PLT-I02 拒绝；FINANCE_ACCOUNTING_MANAGE 也不能代替执行权限。 */
    @ParameterizedTest
    @CsvSource({"PO3,READ", "PO4,READ", "PO3,MANAGE", "PO4,MANAGE"})
    void test_executionNeedsSubledgerManage(String id, String granted) throws Exception {
        platform.grantPermissions(FINANCE_READ_PERMISSION_ID,
            "MANAGE".equals(granted) ? FINANCE_MANAGE_PERMISSION_ID : FINANCE_READ_PERMISSION_ID);

        var row = row(BOUNDARY, id);
        assertDenied(owner(row), send(row));
    }

    /** 只有落后的流读取卡住的批次；account 已无该批次时卡住的批次为空，整页仍 200。 */
    @Test
    void test_progressWithoutStuckPosting() throws Exception {
        var row = row(BOUNDARY, "PO1");
        ledger.reply("GET", row.path("ownerPath").asString(), 200, JSON.writeValueAsString(row.path("ownerResponse")));
        account.reply("GET", row.path("stuckOwnerPath").asString(), 200, "{\"items\":[],\"nextFromSequence\":null}");

        var response = send(row);

        assertEquals(200, response.statusCode(), response.body());
        var streams = JSON.readTree(response.body()).path("streamSegment").path("streams");
        assertAll(() -> assertTrue(streams.get(0).path("stuckPosting").isNull()),
            () -> assertTrue(streams.get(1).path("stuckPosting").isNull()),
            () -> assertEquals(1, account.requests().size(), "一致的流不得读取批次"));
    }

    /** 两条落后的流：account 不可用时两条卡住的批次都为空；返回其他流的批次时只有该条为空，另一条照常带出。 */
    @Test
    void test_stuckPostingFailureIsolated() throws Exception {
        var row = row(BOUNDARY, "PO1");
        var progress = (ObjectNode) row.path("ownerResponse").deepCopy();
        var second = (ObjectNode) progress.path("streamSegment").path("streams").get(1);
        second.put("accountMaxSequence", "45").put("lag", "3").put("status", PostingProgressStatus.BEHIND.getCode());
        ledger.reply("GET", row.path("ownerPath").asString(), 200, JSON.writeValueAsString(progress));
        account.problem("GET", row.path("stuckOwnerPath").asString(), 503, CommonErrorCode.SERVICE_UNAVAILABLE.getCode());

        var unavailable = send(row);

        assertEquals(200, unavailable.statusCode(), unavailable.body());
        var streams = JSON.readTree(unavailable.body()).path("streamSegment").path("streams");
        assertAll(() -> assertTrue(streams.get(0).path("stuckPosting").isNull()),
            () -> assertTrue(streams.get(1).path("stuckPosting").isNull()),
            () -> assertEquals(2, account.requests().size()));

        resetBetweenCalls();
        ledger.reply("GET", row.path("ownerPath").asString(), 200, JSON.writeValueAsString(progress));
        account.reply("GET", row.path("stuckOwnerPath").asString(), 200,
            JSON.writeValueAsString(row.path("stuckOwnerResponse")));

        var partial = send(row);

        assertEquals(200, partial.statusCode(), partial.body());
        var partialStreams = JSON.readTree(partial.body()).path("streamSegment").path("streams");
        assertAll(() -> assertEquals(row.path("response").path("streamSegment").path("streams").get(0).path("stuckPosting"),
                partialStreams.get(0).path("stuckPosting")),
            () -> assertTrue(partialStreams.get(1).path("stuckPosting").isNull(), "分片 1 收到分片 0 的批次应视为读取失败"),
            () -> assertEquals("1", query(account.requests().get(1).query()).get("shardNo")),
            () -> assertEquals("43", query(account.requests().get(1).query()).get("fromSequence")));
    }

    /** billing / account 不可用由 ledger 标为 UNAVAILABLE：段判定原样带出，过账流为空时不读取批次。 */
    @Test
    void test_progressSegmentUnavailable() throws Exception {
        var row = row(BOUNDARY, "PO1");
        var progress = (ObjectNode) row.path("ownerResponse").deepCopy();
        var fact = JSON.createObjectNode().put("status", PostingProgressStatus.UNAVAILABLE.getCode());
        progress.set("factSegment", fact);
        progress.set("streamSegment", JSON.createObjectNode().put("status", PostingProgressStatus.UNAVAILABLE.getCode())
            .set("streams", JSON.createArrayNode()));
        ledger.reply("GET", row.path("ownerPath").asString(), 200, JSON.writeValueAsString(progress));

        var response = send(row);

        assertEquals(200, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(PostingProgressStatus.UNAVAILABLE.getCode(), body.path("factSegment").path("status").asString()),
            () -> assertTrue(body.path("factSegment").path("billingPendingCount").isNull()),
            () -> assertEquals(0, body.path("streamSegment").path("streams").size()),
            () -> assertTrue(account.requests().isEmpty()));
    }

    /** ledger 是聚合入口：不可用 503、账簿不存在 400 LEDGER_BOOK_NOT_FOUND 整页失败，不读取 account。 */
    @Test
    void test_progressLedgerFailure() throws Exception {
        var row = row(BOUNDARY, "PO1");
        ledger.problem("GET", row.path("ownerPath").asString(), 503, CommonErrorCode.SERVICE_UNAVAILABLE.getCode());

        var unavailable = send(row);

        assertEquals(503, unavailable.statusCode(), unavailable.body());
        assertTrue(account.requests().isEmpty());

        resetBetweenCalls();
        ledger.problem("GET", row.path("ownerPath").asString(), 400, LedgerSetupErrorCode.LEDGER_BOOK_NOT_FOUND.getCode());
        assertCodedRejection(LedgerSetupErrorCode.LEDGER_BOOK_NOT_FOUND, send(row));
        assertTrue(account.requests().isEmpty());
    }

    /** 追平的业务拒绝：规则拒绝 400、无 OPEN 期间或并发冲突 409 透出 ledger 的 code 与 detail；account / billing 不可用 503。 */
    @ParameterizedTest(name = "{index} {0} {1}")
    @CsvSource({"400,BUSINESS_CHECK_FAILED", "409,CONCURRENT_REQUEST"})
    void test_catchUpRejectionShowsOwnerDetail(int status, CommonErrorCode code) throws Exception {
        var row = row(BOUNDARY, "PO3");
        ledger.problem("POST", row.path("ownerPath").asString(), status, code.getCode());

        var response = send(row);

        assertEquals(status, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(code.getCode(), body.path("code").asString()),
            () -> assertEquals(FinanceOwnerSimulator.OWNER_DETAIL_CANARY, body.path("detail").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()),
            () -> assertFalse(response.body().contains(FinanceOwnerSimulator.OWNER_REQUEST_ID)));
    }

    /** 追平时 ledger 的依赖不可用：503，不外露 ledger 原文。 */
    @Test
    void test_catchUpDependencyUnavailable() throws Exception {
        var row = row(BOUNDARY, "PO3");
        ledger.problem("POST", row.path("ownerPath").asString(), 503, CommonErrorCode.SERVICE_UNAVAILABLE.getCode());

        var response = send(row);

        assertEquals(503, response.statusCode(), response.body());
        assertFalse(response.body().contains(FinanceOwnerSimulator.OWNER_DETAIL_CANARY));
    }

    /** 重建的业务拒绝：主体与币种下没有过账流 400 STREAM_NOT_FOUND；并发冲突 409。 */
    @Test
    void test_rebuildRejections() throws Exception {
        var row = row(BOUNDARY, "PO4");
        account.problem("POST", row.path("ownerPath").asString(), 400, AccountPostingErrorCode.STREAM_NOT_FOUND.getCode());

        var notFound = send(row);

        assertEquals(400, notFound.statusCode(), notFound.body());
        assertEquals(AccountPostingErrorCode.STREAM_NOT_FOUND.getCode(), JSON.readTree(notFound.body()).path("code").asString());

        resetBetweenCalls();
        account.problem("POST", row.path("ownerPath").asString(), 409, CommonErrorCode.CONCURRENT_REQUEST.getCode());
        var conflict = send(row);
        assertEquals(409, conflict.statusCode(), conflict.body());
        assertEquals(CommonErrorCode.CONCURRENT_REQUEST.getCode(), JSON.readTree(conflict.body()).path("code").asString());
    }

    /** 追平与重建在 Owner 同步执行：等待超过普通 4 秒，到长耗时超时后映射 504 DEPENDENCY_TIMEOUT。 */
    @ParameterizedTest
    @CsvSource({"PO3", "PO4"})
    void test_commandTimeout(String id) throws Exception {
        var row = row(BOUNDARY, id);
        owner(row).hang("POST", row.path("ownerPath").asString());

        long started = System.nanoTime();
        var response = send(row);
        var elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertEquals(504, response.statusCode(), response.body());
        assertEquals(OperationsBffErrorCode.DEPENDENCY_TIMEOUT.getCode(), JSON.readTree(response.body()).path("code").asString());
        assertTrue(elapsed.compareTo(Duration.ofMillis(4500)) > 0, "执行类调用不得使用普通 4 秒超时: " + elapsed);
    }

    /** 批次浏览缺省每页 50 批；返回的批次不属于请求的流或序号小于起始序号时为上游无效响应 502。 */
    @Test
    void test_postingBrowseDefaultsAndUpstreamCheck() throws Exception {
        var row = row(BOUNDARY, "PO2");
        account.reply("GET", row.path("ownerPath").asString(), 200, "{\"items\":[],\"nextFromSequence\":null}");
        var empty = send(row, "?fromSequence=1511", null, true);
        assertEquals(200, empty.statusCode(), empty.body());
        assertEquals("50", query(account.requests().getFirst().query()).get("limit"));

        resetBetweenCalls();
        var foreign = (ObjectNode) row.path("ownerResponse").deepCopy();
        ((ObjectNode) foreign.path("items").get(0)).put("shardNo", "1");
        account.reply("GET", row.path("ownerPath").asString(), 200, JSON.writeValueAsString(foreign));
        assertEquals(502, send(row).statusCode());

        resetBetweenCalls();
        var earlier = (ObjectNode) row.path("ownerResponse").deepCopy();
        ((ObjectNode) earlier.path("items").get(0)).put("postingSequence", "1510");
        account.reply("GET", row.path("ownerPath").asString(), 200, JSON.writeValueAsString(earlier));
        assertEquals(502, send(row).statusCode());
    }

    /**
     * 参数在访问 Owner 前拒绝
     * <li>流身份格式错误或前缀未知、超长主体、缺少或非法起始序号、不允许的每页批次数、未知 Query、追平带 Body、重建缺字段或带多余字段。</li>
     */
    @Test
    void test_invalidParameter() throws Exception {
        var browse = row(BOUNDARY, "PO2");
        for (String path : List.of("/finance/posting-streams/v1/XX.AE001.USD.0/postings",
            "/finance/posting-streams/v1/OE.ae001.USD.0/postings", "/finance/posting-streams/v1/OE.AE001.USD.01/postings",
            "/finance/posting-streams/v1/OE.AE001.USD/postings")) {
            var invalid = (ObjectNode) browse.deepCopy();
            invalid.put("path", path);
            assertRejectedLocally(400, account, send(invalid, "?fromSequence=1", null, true));
            resetBetweenCalls();
        }
        for (String query : List.of("", "?fromSequence=0", "?fromSequence=1&limit=30", "?fromSequence=1&limit=1",
            "?fromSequence=1&shardNo=0")) {
            assertRejectedLocally(400, account, send(browse, query, null, true));
            resetBetweenCalls();
        }

        var catchUp = row(BOUNDARY, "PO3");
        assertRejectedLocally(400, ledger, send(catchUp, "", JSON.createObjectNode(), true));

        resetBetweenCalls();
        var rebuild = row(BOUNDARY, "PO4");
        var missing = (ObjectNode) rebuild.path("request").deepCopy();
        missing.remove("currency");
        assertRejectedLocally(422, account, send(rebuild, "", missing, true));

        resetBetweenCalls();
        var extra = (ObjectNode) rebuild.path("request").deepCopy();
        extra.put("operatorId", "1");
        assertRejectedLocally(400, account, send(rebuild, "", extra, true));

        resetBetweenCalls();
        var progress = (ObjectNode) row(BOUNDARY, "PO1").deepCopy();
        progress.put("path", "/finance/posting-progress/v1/ledger-books/0");
        assertRejectedLocally(400, ledger, send(progress, "", null, true));
    }

    /** 按样例配置 Owner 响应；PO1 另配置卡住的批次。返回主 Owner。 */
    private FinanceOwnerSimulator replyAsOwners(JsonNode row) {
        var owner = owner(row);
        owner.reply(row.path("method").asString(), row.path("ownerPath").asString(), row.path("status").asInt(),
            JSON.writeValueAsString(row.path("ownerResponse")));
        if (row.has("stuckOwnerPath")) {
            account.reply("GET", row.path("stuckOwnerPath").asString(), 200,
                JSON.writeValueAsString(row.path("stuckOwnerResponse")));
        }
        return owner;
    }

    /** 样例的主 Owner。 */
    private FinanceOwnerSimulator owner(JsonNode row) {
        return "ledger".equals(row.path("owner").asString()) ? ledger : account;
    }

    /** 样例中 Owner 请求体的操作人占位替换为会话 Account ID。 */
    private static JsonNode ownerRequest(JsonNode row) {
        return JSON.readTree(JSON.writeValueAsString(row.path("ownerRequest")).replace("<ACCOUNT_ID>", ACCOUNT_ID));
    }
}
