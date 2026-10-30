package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ACCOUNT_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.AUTHORIZE_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.operations.console.bff.test.base.FinanceBrowserAutoTestBase;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RecordedRequest;
import com.paytech.platform.support.api.dataquery.DataQueryExecutionApi;
import com.paytech.platform.support.api.enums.DataQueryErrorCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 与 HttpExchange 维护联合查询键类型并逐层执行联合查询：8 个端点（K1—K5、J1—J3）同路径转发 Platform；
 * 模板新建、修改、详情、版本与校验原样带上 joint / jointQuery
 * <li>Platform 由 Loopback Simulator 承担，PLT-I02 授权与 Owner 调用记录在同一 Simulator。</li>
 * <li>Platform 的 400/403/404/409 业务拒绝与 422 字段错误（含文案）原样到达浏览器，Request ID 为浏览器请求的。</li>
 *
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryKeyTypeController#listQueryKeyTypes(com.paytech.operations.console.bff.service.model.OperatorContext, String, String, Integer, Integer)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryKeyTypeController#createQueryKeyType(com.paytech.operations.console.bff.service.model.OperatorContext, com.paytech.operations.console.bff.service.model.dataquery.request.CreateQueryKeyTypeRequestBody)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryKeyTypeController#updateQueryKeyType(com.paytech.operations.console.bff.service.model.OperatorContext, String, com.paytech.operations.console.bff.service.model.dataquery.request.UpdateQueryKeyTypeRequestBody)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryKeyTypeController#changeQueryKeyTypeStatus(com.paytech.operations.console.bff.service.model.OperatorContext, String, com.paytech.operations.console.bff.service.model.dataquery.request.ChangeQueryKeyTypeStatusRequestBody)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryKeyTypeController#reorderQueryKeyTypes(com.paytech.operations.console.bff.service.model.OperatorContext, com.paytech.operations.console.bff.service.model.dataquery.request.ReorderQueryKeyTypesRequestBody)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryJointController#getJointQuerySettings(com.paytech.operations.console.bff.service.model.OperatorContext)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryJointController#getJointQueryGraph(com.paytech.operations.console.bff.service.model.OperatorContext, String)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryJointController#executeJointQueryStep(com.paytech.operations.console.bff.service.model.OperatorContext, com.paytech.operations.console.bff.service.model.dataquery.request.JointQueryStepRequestBody)
 * @author Claude
 */
class DataQueryJointBrowserAutoTest extends FinanceBrowserAutoTestBase {
    private static final String BOUNDARY = "/data-query-joint-boundary.json";
    private static final String TEMPLATE_BOUNDARY = "/data-query-boundary.json";
    private static final String OWNER_REQUEST_ID = "platform-joint-query-request";
    private static final String CALLER_SERVICE = "operations-console-bff";

    static Stream<JsonNode> endpoints() {
        return rows(BOUNDARY);
    }

    /** 方法、同路径、Query、Body、操作人与调用方 Header 准确转发一次，响应原样返回。 */
    @ParameterizedTest(name = "{index} 联合查询转发")
    @MethodSource("endpoints")
    void test_normalFlow(JsonNode row) throws Exception {
        platform.rawResponse(ownerPath(row), JSON.writeValueAsString(row.path("response")));

        var response = send(row);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(row.path("response"), JSON.readTree(response.body()));
        var call = ownerCall();
        assertAll(() -> assertEquals(row.path("method").asString(), call.method()),
            () -> assertEquals(ownerPath(row), call.path()),
            () -> assertEquals(query(row.path("query").asString()), query(call.query())),
            () -> assertEquals(REQUEST_ID, call.header("X-Request-Id")),
            () -> assertEquals(ACCOUNT_ID, call.header("X-Gateway-Actor-Subject")),
            () -> assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id")),
            () -> assertEquals(CALLER_SERVICE, call.header(DataQueryExecutionApi.CALLER_SERVICE_HEADER)),
            () -> assertNull(call.header("Authorization")), () -> assertNull(call.header("Cookie")),
            () -> assertNull(call.header("Idempotency-Key")));
        if (row.path("request").isNull()) {
            assertTrue(call.body().isEmpty(), "无 Body 的请求不得转发 Body");
        } else {
            assertEquals(row.path("request"), JSON.readTree(call.body()));
        }
    }

    /** 当前会话授权失败时只调用 PLT-I02，不访问 Platform。 */
    @ParameterizedTest(name = "{index} 授权拒绝")
    @MethodSource("endpoints")
    void test_authorizationDenied(JsonNode row) throws Exception {
        platform.allow(false);

        var response = send(row);

        assertEquals(403, response.statusCode(), response.body());
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
    }

    /** 编码重复、被引用不能停用或改值类型、版本冲突、不存在、无权限与入口键类型无效保留状态、Code 与原因，使用浏览器 Request ID。 */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "K2|400|BUSINESS_CHECK_FAILED|键类型编码已存在: ORDER_ID",
        "K3|400|BUSINESS_CHECK_FAILED|键类型被 ACTIVE 模板引用，不能修改 valueType: ORDER_LOOKUP, REFUND_LOOKUP",
        "K3|404|NOT_FOUND|键类型不存在: 7312345678901240001",
        "K3|409|CONCURRENT_REQUEST|键类型已被修改: 7312345678901240001",
        "K4|400|BUSINESS_CHECK_FAILED|键类型被 ACTIVE 模板引用，不能停用: ORDER_LOOKUP",
        "K5|400|BUSINESS_CHECK_FAILED|排序须恰好包含全部键类型且不重复",
        "K5|409|CONCURRENT_REQUEST|键类型已被修改: 7312345678901240002",
        "K1|403|OPERATIONS_PERMISSION_DENIED|无权限",
        "J1|403|OPERATIONS_PERMISSION_DENIED|无权限",
        "J2|400|BUSINESS_CHECK_FAILED|入口键类型不存在、未启用或不允许作为入口: REFUND_ID"})
    void test_businessRuleViolation(String id, int status, String code, String detail) throws Exception {
        var row = row(BOUNDARY, id);
        platform.rawResponse(ownerPath(row), status, "application/problem+json", problem(status, code, detail, null),
            Map.of("X-Request-Id", OWNER_REQUEST_ID));

        var response = send(row);

        assertEquals(status, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(code, body.path("code").asString()),
            () -> assertEquals(detail, body.path("detail").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()),
            () -> assertFalse(response.body().contains(OWNER_REQUEST_ID)));
        ownerCall();
    }

    /** 单层执行遇被查库不可用时保留 DATA_SOURCE_UNAVAILABLE 与安全提示。 */
    @Test
    void test_dataSourceUnavailablePreserved() throws Exception {
        var row = row(BOUNDARY, "J3");
        platform.rawResponse(ownerPath(row), 503, "application/problem+json",
            problem(503, DataQueryErrorCode.DATA_SOURCE_UNAVAILABLE.getCode(), "platform-private-pool-detail", null),
            Map.of("X-Request-Id", OWNER_REQUEST_ID));

        var response = send(row);

        assertEquals(503, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(DataQueryErrorCode.DATA_SOURCE_UNAVAILABLE.getCode(), body.path("code").asString()),
            () -> assertFalse(response.body().contains("platform-private-pool-detail")));
        ownerCall();
    }

    /** 键类型 422：valuePattern 文案保留，Body 外的路径丢弃。 */
    @Test
    void test_keyTypeValidationErrorsKeepPlatformMessages() throws Exception {
        assertValidationProjected(row(BOUNDARY, "K2"), """
            [{"field":"valuePattern","message":"不支持前后断言 (?="},
             {"field":"privateField","message":"remote-unknown"}]
            """, """
            [{"field":"valuePattern","message":"不支持前后断言 (?="}]
            """);
    }

    /** 单层执行 422：keys[0].values[n] 在本次 Body 内保留文案，越界下标丢弃。 */
    @Test
    void test_stepValidationErrorsProjected() throws Exception {
        assertValidationProjected(row(BOUNDARY, "J3"), """
            [{"field":"keys[0].values[0]","message":"不符合订单号格式，如 ORD0000000001"},
             {"field":"keys[0].values[3]","message":"remote-out-of-range"},
             {"field":"depth","message":"层号超过上限"}]
            """, """
            [{"field":"depth","message":"层号超过上限"},
             {"field":"keys[0].values[0]","message":"不符合订单号格式，如 ORD0000000001"}]
            """);
    }

    /** 已加载对象原样转发给 Platform，因已加载而未执行的视图原样返回页面。 */
    @Test
    void test_loadedSubjectsRoundTrip() throws Exception {
        var row = row(BOUNDARY, "J3");
        platform.rawResponse(ownerPath(row), JSON.writeValueAsString(row.path("response")));

        var response = send(row);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(row.path("response").path("alreadyLoaded"), JSON.readTree(response.body()).path("alreadyLoaded"));
        assertEquals(row.path("request").path("loadedSubjects"),
            JSON.readTree(ownerCall().body()).path("loadedSubjects"));
    }

    /** 已加载对象 422：loadedSubjects[i].values[j] 在本次 Body 内保留文案，越界下标丢弃。 */
    @Test
    void test_loadedSubjectValidationErrorsProjected() throws Exception {
        assertValidationProjected(row(BOUNDARY, "J3"), """
            [{"field":"loadedSubjects[1].values[0]","message":"PAYMENT_ID 的值必须为 0..9223372036854775807 的整数"},
             {"field":"loadedSubjects[0].values[2]","message":"remote-out-of-range"}]
            """, """
            [{"field":"loadedSubjects[1].values[0]","message":"PAYMENT_ID 的值必须为 0..9223372036854775807 的整数"}]
            """);
    }

    /** 模板保存 422：joint 内的路径保留 Platform 文案，越界下标丢弃。 */
    @Test
    void test_templateJointValidationErrorsProjected() throws Exception {
        assertValidationProjected(row(TEMPLATE_BOUNDARY, "Q10"), """
            [{"field":"joint.keyParameter.name","message":"键参数 merchantId 必须为必填参数"},
             {"field":"joint.columns[1].keyType","message":"键类型 CARD_FINGERPRINT 未启用"},
             {"field":"joint.columns[5].keyRole","message":"remote-out-of-range"}]
            """, """
            [{"field":"joint.columns[1].keyType","message":"键类型 CARD_FINGERPRINT 未启用"},
             {"field":"joint.keyParameter.name","message":"键参数 merchantId 必须为必填参数"}]
            """);
    }

    /** 读取模板详情后按原样保存新版本：joint 往返不丢失，校验草稿同样带上 joint。 */
    @Test
    void test_templateJointRoundTrip() throws Exception {
        var detail = row(TEMPLATE_BOUNDARY, "Q09");
        platform.rawResponse(ownerPath(detail), JSON.writeValueAsString(detail.path("response")));
        var loaded = JSON.readTree(send(detail).body());
        ownerCall();
        assertEquals(detail.path("response").path("joint"), loaded.path("joint"));
        assertEquals(loaded.path("joint"), loaded.path("currentVersion").path("joint"));
        assertTrue(loaded.path("jointQuery").path("participating").asBoolean());
        resetBetweenCalls();

        var update = row(TEMPLATE_BOUNDARY, "Q11");
        var request = (ObjectNode) update.path("request").deepCopy();
        request.set("joint", loaded.path("currentVersion").path("joint"));
        platform.rawResponse(ownerPath(update), JSON.writeValueAsString(update.path("response")));
        var saved = send(update, "", request, true);
        assertEquals(200, saved.statusCode(), saved.body());
        assertEquals(detail.path("response").path("joint"), JSON.readTree(ownerCall().body()).path("joint"));
        resetBetweenCalls();

        var validate = row(TEMPLATE_BOUNDARY, "Q16");
        platform.rawResponse(ownerPath(validate), JSON.writeValueAsString(validate.path("response")));
        assertEquals(200, send(validate).statusCode());
        assertEquals(validate.path("request").path("joint"), JSON.readTree(ownerCall().body()).path("joint"));
    }

    /** 未知或非法 Query、缺少入口键类型、非法路径、未知或强转字段、缺少必填字段均在访问 Platform 前拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        var list = row(BOUNDARY, "K1");
        assertRejectedBeforePlatform(400, send(list, "?unknown=1", JSON.nullNode(), true));
        assertRejectedBeforePlatform(400, send(list, "?status=DELETED", JSON.nullNode(), true));

        var create = row(BOUNDARY, "K2");
        assertRejectedBeforePlatform(400, send(create, "", with(create, "unknown", "x"), true));
        assertRejectedBeforePlatform(400, send(create, "", with(create, "sortNo", "10"), true));
        assertRejectedBeforePlatform(422, send(create, "", with(create, "keyTypeCode", "order_id"), true));
        assertRejectedBeforePlatform(422, send(create, "", with(create, "valueType", "DECIMAL"), true));
        assertRejectedBeforePlatform(422, send(create, "", without(create, "entryAllowed"), true));

        var update = row(BOUNDARY, "K3");
        assertRejectedBeforePlatform(400, send(at(update, "/data-query-admin/v1/key-types/abc"), "",
            update.path("request"), true));
        assertRejectedBeforePlatform(422, send(update, "", without(update, "expectedVersion"), true));

        var status = row(BOUNDARY, "K4");
        assertRejectedBeforePlatform(422, send(status, "", with(status, "status", "DELETED"), true));

        var order = row(BOUNDARY, "K5");
        assertRejectedBeforePlatform(422, send(order, "", JSON.createObjectNode().set("items",
            JSON.createArrayNode()), true));

        var graph = row(BOUNDARY, "J2");
        assertRejectedBeforePlatform(400, send(graph, "", JSON.nullNode(), true));
        assertRejectedBeforePlatform(400, send(graph, "?entryKeyType=order_id", JSON.nullNode(), true));

        var step = row(BOUNDARY, "J3");
        assertRejectedBeforePlatform(400, send(step, "", with(step, "depth", "0"), true));
        assertRejectedBeforePlatform(422, send(step, "", with(step, "depth", -1), true));
        var noValues = (ObjectNode) step.path("request").deepCopy();
        ((ObjectNode) noValues.path("keys").get(0)).set("values", JSON.createArrayNode());
        assertRejectedBeforePlatform(422, send(step, "", noValues, true));
        var noLoadedValues = (ObjectNode) step.path("request").deepCopy();
        ((ObjectNode) noLoadedValues.path("loadedSubjects").get(0)).set("values", JSON.createArrayNode());
        assertRejectedBeforePlatform(422, send(step, "", noLoadedValues, true));
        var unknownLoaded = (ObjectNode) step.path("request").deepCopy();
        ((ObjectNode) unknownLoaded.path("loadedSubjects").get(0)).put("templateCode", "ORDER_LOOKUP");
        assertRejectedBeforePlatform(400, send(step, "", unknownLoaded, true));
        var badLoadedKey = (ObjectNode) step.path("request").deepCopy();
        ((ObjectNode) badLoadedKey.path("loadedSubjects").get(0)).put("keyType", "order_id");
        assertRejectedBeforePlatform(422, send(step, "", badLoadedKey, true));

        var template = row(TEMPLATE_BOUNDARY, "Q10");
        var unknownJoint = (ObjectNode) template.path("request").deepCopy();
        ((ObjectNode) unknownJoint.path("joint")).put("unknown", "x");
        assertRejectedBeforePlatform(400, send(template, "", unknownJoint, true));
        var badRole = (ObjectNode) template.path("request").deepCopy();
        ((ObjectNode) badRole.path("joint").path("columns").get(0)).put("keyRole", "FILTER");
        assertRejectedBeforePlatform(422, send(template, "", badRole, true));
    }

    /** 写回执类型、身份或状态不符，排序结果与本次顺序不符，层号不符均按上游无效响应 502。 */
    @Test
    void test_invalidUpstreamResponse() throws Exception {
        var list = row(BOUNDARY, "K1");
        assertUpstreamInvalid(list, withResponse(list, "size", 10));
        assertUpstreamInvalid(list, withItem(list, 1, "keyTypeId", "7312345678901240001"));

        var create = row(BOUNDARY, "K2");
        assertUpstreamInvalid(create, withResponse(create, "objectType", "QUERY_TEMPLATE"));
        assertUpstreamInvalid(create, withResponse(create, "objectCode", "PAYMENT_ID"));
        assertUpstreamInvalid(create, withResponse(create, "status", "INACTIVE"));

        var update = row(BOUNDARY, "K3");
        assertUpstreamInvalid(update, withResponse(update, "objectId", "7312345678901240002"));
        var status = row(BOUNDARY, "K4");
        assertUpstreamInvalid(status, withResponse(status, "status", "ACTIVE"));

        var order = row(BOUNDARY, "K5");
        var reversed = (ObjectNode) order.path("response").deepCopy();
        var items = (ArrayNode) reversed.path("items");
        items.insert(0, items.remove(1));
        assertUpstreamInvalid(order, reversed);
        var zeroSort = (ObjectNode) order.path("response").deepCopy();
        ((ObjectNode) zeroSort.path("items").get(0)).put("sortNo", 0);
        assertUpstreamInvalid(order, zeroSort);

        var step = row(BOUNDARY, "J3");
        assertUpstreamInvalid(step, withResponse(step, "depth", 2));
    }

    /** 带 errors 的 Platform 422 只保留本次请求可定位的字段，文案原样。 */
    private void assertValidationProjected(JsonNode row, String remote, String expected) throws Exception {
        platform.rawResponse(ownerPath(row), 422, "application/problem+json",
            problem(422, CommonErrorCode.VALIDATION_ERROR.getCode(), "校验未通过", remote),
            Map.of("X-Request-Id", OWNER_REQUEST_ID));

        var response = send(row);

        assertEquals(422, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(CommonErrorCode.VALIDATION_ERROR.getCode(), body.path("code").asString()),
            () -> assertEquals(JSON.readTree(expected), body.path("errors")),
            () -> assertFalse(response.body().contains("remote-")));
        ownerCall();
    }

    /** Platform 严格 Problem 响应体；errors 为 JSON 数组文本或 null。 */
    private static String problem(int status, String code, String detail, String errors) {
        var body = JSON.createObjectNode().put("type", "about:blank")
            .put("title", HttpStatus.valueOf(status).getReasonPhrase()).put("detail", detail)
            .put("code", code).put("requestId", OWNER_REQUEST_ID).put("timestamp", "2026-10-29T00:00:00.123456Z");
        if (errors != null) {
            body.set("errors", JSON.readTree(errors));
        }
        return JSON.writeValueAsString(body);
    }

    /** Platform 响应 502 且本次只调用一次 Owner。 */
    private void assertUpstreamInvalid(JsonNode row, JsonNode upstream) throws Exception {
        resetBetweenCalls();
        platform.rawResponse(ownerPath(row), JSON.writeValueAsString(upstream));
        var response = send(row);
        assertEquals(502, response.statusCode(), response.body());
        ownerCall();
    }

    /** 本地拒绝：指定状态且 Platform 至多收到授权请求；之后为下一次调用重置记录。 */
    private void assertRejectedBeforePlatform(int status, HttpResponse<String> response) {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(platform.requests().stream().map(RecordedRequest::path).allMatch(AUTHORIZE_PATH::equals),
            platform.requests().toString());
        resetBetweenCalls();
    }

    /** 授权之后恰好一次 Owner 调用，返回该调用。 */
    private RecordedRequest ownerCall() {
        var requests = platform.requests();
        assertEquals(2, requests.size(), requests.toString());
        assertEquals(AUTHORIZE_PATH, requests.getFirst().path());
        return requests.getLast();
    }

    /** 复制样例并替换浏览器路径。 */
    private static JsonNode at(JsonNode row, String path) {
        return ((ObjectNode) row.deepCopy()).put("path", path);
    }

    /** 复制样例请求并覆盖一个字段。 */
    private static ObjectNode with(JsonNode row, String field, Object value) {
        return put((ObjectNode) row.path("request").deepCopy(), field, value);
    }

    /** 复制样例请求并删除一个字段。 */
    private static ObjectNode without(JsonNode row, String field) {
        var copy = (ObjectNode) row.path("request").deepCopy();
        copy.remove(field);
        return copy;
    }

    /** 复制样例响应并覆盖一个字段。 */
    private static ObjectNode withResponse(JsonNode row, String field, Object value) {
        return put((ObjectNode) row.path("response").deepCopy(), field, value);
    }

    /** 复制分页响应并覆盖指定条目的一个字段。 */
    private static ObjectNode withItem(JsonNode row, int index, String field, Object value) {
        var copy = (ObjectNode) row.path("response").deepCopy();
        put((ObjectNode) ((ArrayNode) copy.path("items")).get(index), field, value);
        return copy;
    }

    /** 按值类型写入字段。 */
    private static ObjectNode put(ObjectNode node, String field, Object value) {
        if (value instanceof Integer number) {
            node.put(field, number);
        } else {
            node.put(field, value.toString());
        }
        return node;
    }
}
