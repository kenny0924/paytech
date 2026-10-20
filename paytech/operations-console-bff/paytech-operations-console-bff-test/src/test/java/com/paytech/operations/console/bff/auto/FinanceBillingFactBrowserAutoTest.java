package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ACCOUNT_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.FINANCE_APPROVE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.FINANCE_MANAGE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.FINANCE_READ_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.billing.api.enums.BillingFactErrorCode;
import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.ext.rest.FinanceBillingFactController;
import com.paytech.operations.console.bff.test.base.FinanceBrowserAutoTestBase;
import com.paytech.operations.console.bff.test.simulator.FinanceOwnerSimulator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 与 HttpExchange 转发计费事实的 5 个端点（B1—B5：检索、登记、详情、批准、作废）；billing 由 Loopback Simulator 承担
 * <li>浏览器路径 /finance/billing-facts/v1 对应 billing /billing/v1/facts，列表对应 /search。</li>
 *
 * @see FinanceBillingFactController
 * @author Claude
 */
class FinanceBillingFactBrowserAutoTest extends FinanceBrowserAutoTestBase {
    private static final String BOUNDARY = "/finance-adjustment-boundary.json";
    private static final String SUBMIT = "B2";
    private static final String SOURCE_REFERENCE = "OPS-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";

    /** 原样转发的端点；登记会补充来源字段，单独验证。 */
    static Stream<JsonNode> forwardedEndpoints() {
        return endpoints().filter(row -> !SUBMIT.equals(row.path("id").asString()));
    }

    static Stream<JsonNode> endpoints() {
        return rows(BOUNDARY).filter(row -> row.path("id").asString().startsWith("B"));
    }

    /** 检索、详情、批准与作废的方法、Owner 路径、Query 与操作人 Header 准确转发；批准与作废不带 Body。 */
    @ParameterizedTest(name = "{index} 计费事实转发")
    @MethodSource("forwardedEndpoints")
    void test_normalFlow(JsonNode row) throws Exception {
        replyAsOwner(billing, row);

        assertForwarded(billing, row, send(row));
    }

    /** 当前会话授权失败时不访问 billing。 */
    @ParameterizedTest(name = "{index} 授权拒绝")
    @MethodSource("endpoints")
    void test_authorizationDenied(JsonNode row) throws Exception {
        platform.allow(false);

        assertDenied(billing, send(row));
    }

    /** 登记固定来源系统 OPERATIONS，每次生成新的 OPS- + UUID 来源引用；其余字段与操作人 Header 原样转发。 */
    @Test
    void test_submitFixesSourceSystemAndGeneratesReference() throws Exception {
        var submit = row(BOUNDARY, SUBMIT);
        replyAsOwner(billing, submit);
        var first = send(submit);
        var second = send(submit);

        assertEquals(201, first.statusCode(), first.body());
        assertEquals(submit.path("response"), JSON.readTree(first.body()));
        assertEquals(2, billing.requests().size());
        var references = billing.requests().stream().map(call -> {
            var body = (ObjectNode) JSON.readTree(call.body());
            String reference = body.path("sourceReference").asString();
            assertAll(() -> assertEquals("POST", call.method()), () -> assertEquals(ownerPath(submit), call.path()),
                () -> assertEquals("OPERATIONS", body.path("sourceSystem").asString()),
                () -> assertTrue(reference.matches(SOURCE_REFERENCE), reference),
                () -> assertEquals(ACCOUNT_ID, call.header("X-Gateway-Actor-Subject")),
                () -> assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id")),
                () -> assertNull(call.header("Idempotency-Key")), () -> assertNull(call.header("Authorization")));
            body.remove("sourceSystem");
            body.remove("sourceReference");
            assertEquals(submit.path("request"), body);
            return reference;
        }).toList();
        assertEquals(201, second.statusCode(), second.body());
        assertNotEquals(references.get(0), references.get(1));
    }

    /** 页面不能自带来源系统、来源引用或编制人：未知字段 400，不访问 billing。 */
    @ParameterizedTest
    @CsvSource({"sourceSystem,PARTNER_PORTAL", "sourceReference,OPS-fixed", "makerId,10001"})
    void test_submitRejectsSourceFields(String field, String value) throws Exception {
        var submit = row(BOUNDARY, SUBMIT);
        var body = (ObjectNode) submit.path("request").deepCopy();
        body.put(field, value);

        assertRejectedLocally(400, billing, send(submit, "", body, true));
    }

    /** 批准需要 APPROVE；登记与作废需要 MANAGE；只有 READ 只能查询。 */
    @ParameterizedTest
    @CsvSource({"B4,MANAGE", "B2,APPROVE", "B5,APPROVE", "B5,READ"})
    void test_separatedPermissions(String id, String granted) throws Exception {
        platform.grantPermissions(FINANCE_READ_PERMISSION_ID, switch (granted) {
            case "MANAGE" -> FINANCE_MANAGE_PERMISSION_ID;
            case "APPROVE" -> FINANCE_APPROVE_PERMISSION_ID;
            default -> FINANCE_READ_PERMISSION_ID;
        });

        assertDenied(billing, send(row(BOUNDARY, id)));
    }

    /** 登记、批准与作废的 BillingFactErrorCode 拒绝（D4）Code 与默认消息原样到达，不外露 billing 原文。 */
    @ParameterizedTest(name = "{index} {0} {1}")
    @CsvSource({"B2,FACT_LINE_INVALID", "B2,FACT_SUPERSEDE_INVALID", "B4,SELF_REVIEW_FORBIDDEN", "B4,FACT_NOT_DRAFT",
        "B4,FACT_SUPERSEDE_INVALID", "B5,FACT_NOT_DRAFT"})
    void test_businessRuleViolation(String id, BillingFactErrorCode code) throws Exception {
        var row = row(BOUNDARY, id);
        billing.problem("POST", ownerPath(row), 400, code.getCode());

        assertCodedRejection(code, send(row));
    }

    /** 作废后的事实以 VOIDED 状态返回；未登记的公共业务拒绝保留公共 Code，并发冲突 409，均不外露原文。 */
    @Test
    void test_voidAndCommonRejection() throws Exception {
        var voidRow = row(BOUNDARY, "B5");
        replyAsOwner(billing, voidRow);
        assertEquals(BillingFactStatus.VOIDED.getCode(), JSON.readTree(send(voidRow).body()).path("status").asString());

        resetBetweenCalls();
        billing.problem("POST", ownerPath(voidRow), 400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode());
        assertCodedRejection(CommonErrorCode.BUSINESS_CHECK_FAILED, send(voidRow));

        resetBetweenCalls();
        var approve = row(BOUNDARY, "B4");
        billing.problem("POST", ownerPath(approve), 409, CommonErrorCode.CONCURRENT_REQUEST.getCode());
        var response = send(approve);
        assertEquals(409, response.statusCode(), response.body());
        assertFalse(response.body().contains(FinanceOwnerSimulator.OWNER_DETAIL_CANARY));
    }

    /** 缺少主体或币种、非法状态与时间、未知 Query、批准或作废带 Body 均在访问 billing 前拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        var search = row(BOUNDARY, "B1");
        assertRejectedLocally(400, billing, send(search, "?entityNo=AE001&page=1&size=10", search.path("request"), true));

        resetBetweenCalls();
        assertRejectedLocally(400, billing, send(search, "?entityNo=AE000&currency=USD", search.path("request"), true));

        resetBetweenCalls();
        assertRejectedLocally(400, billing, send(search, "?entityNo=AE001&currency=USD&status=PUBLISHED",
            search.path("request"), true));

        resetBetweenCalls();
        assertRejectedLocally(400, billing, send(search, "?entityNo=AE001&currency=USD&createdFrom=2026-10-01",
            search.path("request"), true));

        resetBetweenCalls();
        assertRejectedLocally(400, billing, send(search, search.path("query").asString() + "&entityType=OPERATING_ENTITY",
            search.path("request"), true));

        resetBetweenCalls();
        assertRejectedLocally(400, billing, send(row(BOUNDARY, "B4"), "", JSON.createObjectNode(), true));

        resetBetweenCalls();
        assertRejectedLocally(400, billing, send(row(BOUNDARY, "B5"), "", JSON.createObjectNode(), true));

        resetBetweenCalls();
        var submit = row(BOUNDARY, SUBMIT);
        var noLines = (ObjectNode) submit.path("request").deepCopy();
        noLines.set("lines", JSON.createArrayNode());
        assertRejectedLocally(422, billing, send(submit, "", noLines, true));
    }

    /** 详情或命令回执的事实 ID 与请求不一致、明细行缺失时视为上游无效响应 502。 */
    @Test
    void test_invalidUpstreamResponse() throws Exception {
        var detail = row(BOUNDARY, "B3");
        var other = (ObjectNode) detail.path("response").deepCopy();
        other.put("factId", "7400000000000999");
        billing.reply("GET", ownerPath(detail), 200, JSON.writeValueAsString(other));
        assertEquals(502, send(detail).statusCode());

        resetBetweenCalls();
        var approve = row(BOUNDARY, "B4");
        var noLines = (ObjectNode) approve.path("response").deepCopy();
        noLines.putNull("lines");
        billing.reply("POST", ownerPath(approve), 200, JSON.writeValueAsString(noLines));
        assertEquals(502, send(approve).statusCode());

        resetBetweenCalls();
        var search = row(BOUNDARY, "B1");
        var total = (ObjectNode) search.path("response").deepCopy();
        total.put("total", 3);
        billing.reply("GET", ownerPath(search), 200, JSON.writeValueAsString(total));
        assertEquals(502, send(search).statusCode());
    }

    /** billing 使用普通读取超时：挂起时映射 504 DEPENDENCY_TIMEOUT，不外露地址。 */
    @Test
    void test_ownerTimeout() throws Exception {
        var detail = row(BOUNDARY, "B3");
        billing.hang("GET", ownerPath(detail));

        var response = send(detail);

        assertEquals(504, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(OperationsBffErrorCode.DEPENDENCY_TIMEOUT.getCode(), body.path("code").asString()),
            () -> assertFalse(response.body().contains("127.0.0.1")));
    }
}
