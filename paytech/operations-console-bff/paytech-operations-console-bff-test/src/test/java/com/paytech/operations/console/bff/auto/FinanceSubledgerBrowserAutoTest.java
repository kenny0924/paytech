package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.service.model.FinanceSubledgerModels.BALANCE_BUCKET_PATTERN;
import static com.paytech.operations.console.bff.service.model.FinanceSubledgerModels.ENTITY_TYPE_PATTERN;
import static com.paytech.operations.console.bff.service.model.FinanceSubledgerModels.OWNER_TYPE_PATTERN;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.AccountPostingErrorCode;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.BalanceMovementErrorCode;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.ext.rest.FinanceSubledgerController;
import com.paytech.operations.console.bff.test.base.FinanceBrowserAutoTestBase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 与 HttpExchange 转发明细账查询的 3 个端点（R19—R21）；account 由 Loopback Simulator 承担
 *
 * @see FinanceSubledgerController
 * @author Claude
 */
class FinanceSubledgerBrowserAutoTest extends FinanceBrowserAutoTestBase {
    private static final String BOUNDARY = "/finance-subledger-boundary.json";

    static Stream<JsonNode> endpoints() {
        return rows(BOUNDARY);
    }

    static Stream<Arguments> codedRejections() {
        return Stream.of(Arguments.of("R20", BalanceMovementErrorCode.ACCOUNT_NOT_FOUND),
            Arguments.of("R21", AccountPostingErrorCode.POSTING_NOT_FOUND));
    }

    /** 路径与 Query 准确转发，账户、流水与批次原样返回（含空来源字段与 UTC 时间）；查询不携带操作人 Header。 */
    @ParameterizedTest(name = "{index} 明细账转发")
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

    /** 缺省分页按公共默认值 page=1、size=10 转发；显式分页原样转发并回显 account 的页码、条数与总数。 */
    @Test
    void test_pagingForwardedAndEchoed() throws Exception {
        var accounts = row(BOUNDARY, "R19");
        replyAsOwner(account, accounts);
        var response = send(accounts, "?entityType=OPERATING_ENTITY&entityId=AE001&currency=USD", null, true);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(Map.of("entityType", "OPERATING_ENTITY", "entityId", "AE001", "currency", "USD", "page", "1",
            "size", "10"), query(account.requests().getFirst().query()));

        resetBetweenCalls();
        var entries = row(BOUNDARY, "R20");
        var page = (ObjectNode) entries.path("response").deepCopy();
        page.put("page", 2).put("size", 50).put("total", 51);
        var items = (ArrayNode) page.path("items");
        while (items.size() > 1) {
            items.remove(items.size() - 1);
        }
        account.reply("GET", ownerPath(entries), 200, JSON.writeValueAsString(page));
        var second = send(entries, "?page=2&size=50", null, true);
        assertEquals(200, second.statusCode(), second.body());
        assertAll(() -> assertEquals(Map.of("page", "2", "size", "50"), query(account.requests().getFirst().query())),
            () -> assertEquals(page, JSON.readTree(second.body())));
    }

    /** 流水的事实类型与批次的发布时间原样透传：桶移动流水的事实类型为空，尚未发布的批次发布时间为空。 */
    @Test
    void test_factTypeAndPublishedTimeForwarded() throws Exception {
        var entries = row(BOUNDARY, "R20");
        replyAsOwner(account, entries);
        var items = JSON.readTree(send(entries).body()).path("items");
        assertAll(() -> assertEquals(entries.path("response").path("items").path(0).path("factType"), items.path(0).path("factType")),
            () -> assertTrue(items.path(2).path("factType").isNull()));

        resetBetweenCalls();
        var posting = row(BOUNDARY, "R21");
        replyAsOwner(account, posting);
        assertEquals(posting.path("response").path("publishedTime"), JSON.readTree(send(posting).body()).path("publishedTime"));

        resetBetweenCalls();
        var unpublished = (ObjectNode) posting.path("response").deepCopy();
        unpublished.putNull("publishedTime");
        account.reply("GET", ownerPath(posting), 200, JSON.writeValueAsString(unpublished));
        var response = send(posting);
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(JSON.readTree(response.body()).path("publishedTime").isNull());
    }

    /** account 登记的业务拒绝（流水的账户不存在、批次不存在）以 400 与枚举 Code 原样到达浏览器。 */
    @ParameterizedTest
    @MethodSource("codedRejections")
    void test_noDataFound(String id, CodeMessageEnum code) throws Exception {
        var row = row(BOUNDARY, id);
        account.problem("GET", ownerPath(row), 400, code.getCode());

        assertCodedRejection(code, send(row));
    }

    /** 三个枚举 Query 的校验正则与 account 合同的全部 Code 完全一致。 */
    @Test
    void test_queryPatternsMatchAccountContract() {
        assertAll(() -> assertEquals(codes(AccountEntityType.values()), ENTITY_TYPE_PATTERN),
            () -> assertEquals(codes(OwnerType.values()), OWNER_TYPE_PATTERN),
            () -> assertEquals(codes(BalanceBucket.values()), BALANCE_BUCKET_PATTERN));
    }

    /**
     * Query 白名单与格式在访问 account 前拒绝
     * <li>缺少必填主体参数、ledger 专有的核算对象类型、未知 Query、非法分页、非法路径变量、批次详情带 Query、查询带 Body。</li>
     */
    @Test
    void test_invalidParameter() throws Exception {
        var accounts = row(BOUNDARY, "R19");
        assertRejectedLocally(400, account, send(accounts, "?entityType=OPERATING_ENTITY&entityId=AE001", null, true));

        resetBetweenCalls();
        assertRejectedLocally(400, account, send(accounts, "?entityType=OPERATING_ENTITY&entityId=AE001&currency=USD"
            + "&ownerType=" + LedgerOwnerType.OPERATING_ENTITY.getCode(), null, true));

        resetBetweenCalls();
        assertRejectedLocally(400, account, send(accounts, accounts.path("query").asString() + "&normalSide=CREDIT",
            null, true));

        resetBetweenCalls();
        var entries = row(BOUNDARY, "R20");
        assertRejectedLocally(400, account, send(entries, "?page=1&size=7", null, true));

        resetBetweenCalls();
        assertRejectedLocally(400, account, send(entries, "?fromSequence=1", null, true));

        resetBetweenCalls();
        var posting = (ObjectNode) row(BOUNDARY, "R21").deepCopy();
        posting.put("path", "/finance/account-postings/v1/not-an-id");
        assertRejectedLocally(400, account, send(posting, "", null, true));

        resetBetweenCalls();
        var detail = row(BOUNDARY, "R21");
        assertRejectedLocally(400, account, send(detail, "?page=1", null, true));

        resetBetweenCalls();
        assertRejectedLocally(400, account, send(detail, "", JSON.createObjectNode(), true));
    }

    /** 批次主键不一致、批次缺少流水列表或分页总数不自洽时视为上游无效响应 502。 */
    @Test
    void test_invalidUpstreamResponse() throws Exception {
        var detail = row(BOUNDARY, "R21");
        var other = (ObjectNode) detail.path("response").deepCopy();
        other.put("postingId", "7318802201114009999");
        account.reply("GET", ownerPath(detail), 200, JSON.writeValueAsString(other));
        assertEquals(502, send(detail).statusCode());

        resetBetweenCalls();
        var missing = (ObjectNode) detail.path("response").deepCopy();
        missing.remove("entries");
        account.reply("GET", ownerPath(detail), 200, JSON.writeValueAsString(missing));
        assertEquals(502, send(detail).statusCode());

        resetBetweenCalls();
        var accounts = row(BOUNDARY, "R19");
        var page = (ObjectNode) accounts.path("response").deepCopy();
        page.put("total", 5);
        account.reply("GET", ownerPath(accounts), 200, JSON.writeValueAsString(page));
        assertEquals(502, send(accounts).statusCode());
    }

    /** account 读取超时映射 504 DEPENDENCY_TIMEOUT。 */
    @Test
    void test_ownerTimeout() throws Exception {
        var row = row(BOUNDARY, "R21");
        account.hang("GET", ownerPath(row));

        var response = send(row);

        assertEquals(504, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(OperationsBffErrorCode.DEPENDENCY_TIMEOUT.getCode(), body.path("code").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()));
    }

    /** 枚举 Code 按声明顺序以 | 连接，即注解中使用的正则。 */
    private static String codes(CodeEnum[] values) {
        return Arrays.stream(values).map(CodeEnum::getCode).collect(Collectors.joining("|"));
    }
}
