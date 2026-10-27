package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.AUTHORIZE_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.UPSTREAM_DETAIL_CANARY;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.operations.console.bff.ext.rest.ReferenceDataAdminController;
import com.paytech.operations.console.bff.test.base.FinanceBrowserAutoTestBase;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RecordedRequest;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 与 HttpExchange 只读查看 Platform 参考数据（"参考数据"页 4 个端点 R01—R04）
 * <li>Platform 由 Loopback Simulator 承担，PLT-I02 授权与参考数据查询记录在同一 Simulator；查询不携带操作人。</li>
 * <li>边界清单每行给出页面 Query、Platform Query（ownerQuery）、Platform 响应与页面响应。</li>
 *
 * @see ReferenceDataAdminController
 * @author Claude
 */
class ReferenceDataBrowserAutoTest extends FinanceBrowserAutoTestBase {
    private static final String BOUNDARY = "/reference-data-boundary.json";

    static Stream<JsonNode> endpoints() {
        return rows(BOUNDARY);
    }

    /** Query 按合同转发一次（关键字去除首尾空白），响应投影为页面模型；不转发操作人与浏览器凭据。 */
    @ParameterizedTest(name = "{index} 参考数据转发")
    @MethodSource("endpoints")
    void test_normalFlow(JsonNode row) throws Exception {
        platform.rawResponse(ownerPath(row), JSON.writeValueAsString(row.path("ownerResponse")));

        var response = send(row);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(row.path("response"), JSON.readTree(response.body()));
        var call = ownerCall();
        assertAll(() -> assertEquals("GET", call.method()),
            () -> assertEquals(query(row.path("ownerQuery").asString()), query(call.query())),
            () -> assertEquals(REQUEST_ID, call.header("X-Request-Id")),
            () -> assertNull(call.header("X-Gateway-Actor-Subject")), () -> assertNull(call.header("X-Gateway-Session-Id")),
            () -> assertNull(call.header("Authorization")), () -> assertNull(call.header("Cookie")),
            () -> assertNull(call.header("Idempotency-Key")), () -> assertTrue(call.body().isEmpty()));
    }

    /** 当前会话授权失败时只调用 PLT-I02，不访问 Platform 参考数据。 */
    @ParameterizedTest(name = "{index} 授权拒绝")
    @MethodSource("endpoints")
    void test_authorizationDenied(JsonNode row) throws Exception {
        platform.allow(false);

        var response = send(row);

        assertEquals(403, response.statusCode(), response.body());
        assertOnlyAuthorized();
    }

    /** 未知、重复、空或越界 Query 在访问 Platform 参考数据前拒绝（Query 边界可能先于授权）。 */
    @ParameterizedTest
    @CsvSource({"R01,?codes=AE", "R01,?status=ENABLED", "R01,?status=ACTIVE&status=DRAFT", "R01,?status=",
        "R02,?countryCode=ae", "R02,?countryCode=ARE", "R03,?level=2", "R04,?size=15", "R04,?page=0", "R04,?codes=5816"})
    void test_invalidParameter(String id, String query) throws Exception {
        var response = send(row(BOUNDARY, id), query, JSON.nullNode(), true);

        assertEquals(400, response.statusCode(), response.body());
        assertNoReferenceDataCall();
    }

    /** 查询带 Body 在访问 Platform 前拒绝。 */
    @Test
    void test_bodyRejected() throws Exception {
        var response = send(row(BOUNDARY, "R01"), "", JSON.createObjectNode().put("status", "ACTIVE"), true);

        assertEquals(400, response.statusCode(), response.body());
        assertNoReferenceDataCall();
    }

    /** 未知状态、非法编码、重复编码、层级与上级不一致、分页窗口不符均按上游无效响应 502。 */
    @Test
    void test_invalidUpstreamResponse() throws Exception {
        var countries = row(BOUNDARY, "R01");
        assertUpstreamInvalid(countries, withItem(countries, 0, "status", ReferenceDataStatus.ACTIVE.getCode().toLowerCase()));
        assertUpstreamInvalid(countries, withItem(countries, 0, "countryCode", "ARE"));
        assertUpstreamInvalid(countries, withItem(countries, 1, "countryCode", "AE"));
        var subdivisions = row(BOUNDARY, "R02");
        assertUpstreamInvalid(subdivisions, withItem(subdivisions, 0, "subdivisionCode", "AE_AZ"));
        assertUpstreamInvalid(subdivisions, withItem(subdivisions, 0, "subdivisionCode", "SA-01"));
        var industries = row(BOUNDARY, "R03");
        assertUpstreamInvalid(industries, withItem(industries, 0, "parentIndustryCode", "OTHER"));
        assertUpstreamInvalid(industries, withItem(industries, 1, "level", 3));
        assertUpstreamInvalid(industries, withItem(industries, 1, "defaultMccCode", "565"));
        var mccs = row(BOUNDARY, "R04");
        assertUpstreamInvalid(mccs, withItem(mccs, 0, "mccCode", "58A6"));
        var page = (ObjectNode) mccs.path("ownerResponse").deepCopy();
        page.put("page", 1);
        assertUpstreamInvalid(mccs, page);
    }

    /** Platform 503 映射为依赖不可用，不外露 Platform 原文。 */
    @ParameterizedTest
    @CsvSource({"R01", "R04"})
    void test_ownerUnavailable(String id) throws Exception {
        var row = row(BOUNDARY, id);
        platform.problem(ownerPath(row), 503, "SERVICE_UNAVAILABLE");

        var response = send(row);

        assertEquals(503, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals("DEPENDENCY_UNAVAILABLE", body.path("code").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()),
            () -> assertFalse(response.body().contains(UPSTREAM_DETAIL_CANARY)));
        ownerCall();
    }

    /** Platform 返回合同外字段时 BFF 不投影该字段。 */
    @Test
    void test_unknownUpstreamFieldNotProjected() throws Exception {
        var row = row(BOUNDARY, "R01");
        platform.rawResponse(ownerPath(row), JSON.writeValueAsString(withItem(row, 0, "internalNote", "OWNER-ONLY-CANARY")));

        var response = send(row);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(row.path("response"), JSON.readTree(response.body()));
    }

    /** Platform 响应 502 且本次只调用一次 Platform。 */
    private void assertUpstreamInvalid(JsonNode row, JsonNode upstream) throws Exception {
        resetBetweenCalls();
        platform.rawResponse(ownerPath(row), JSON.writeValueAsString(upstream));
        var response = send(row);
        assertEquals(502, response.statusCode(), response.body());
        ownerCall();
    }

    /** Platform 只收到一次 PLT-I02 授权。 */
    private void assertOnlyAuthorized() {
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
    }

    /** Platform 至多收到授权请求，没有参考数据调用。 */
    private void assertNoReferenceDataCall() {
        assertTrue(platform.requests().stream().map(RecordedRequest::path).allMatch(AUTHORIZE_PATH::equals),
            platform.requests().toString());
    }

    /** 授权之后恰好一次参考数据调用，返回该调用。 */
    private RecordedRequest ownerCall() {
        var requests = platform.requests();
        assertEquals(2, requests.size(), requests.toString());
        assertEquals(AUTHORIZE_PATH, requests.getFirst().path());
        return requests.getLast();
    }

    /** 复制 Platform 响应并覆盖指定条目的一个字段。 */
    private static ObjectNode withItem(JsonNode row, int index, String field, Object value) {
        var copy = (ObjectNode) row.path("ownerResponse").deepCopy();
        var item = (ObjectNode) ((ArrayNode) copy.path("items")).get(index);
        if (value instanceof Integer number) {
            item.put(field, number);
        } else {
            item.put(field, value.toString());
        }
        return copy;
    }
}
