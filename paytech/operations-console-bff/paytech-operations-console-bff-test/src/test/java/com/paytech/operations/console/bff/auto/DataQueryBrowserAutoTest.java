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
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.test.base.FinanceBrowserAutoTestBase;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RecordedRequest;
import com.paytech.platform.support.api.dataquery.DataQueryExecutionApi;
import com.paytech.platform.support.api.enums.DataQueryErrorCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 与 HttpExchange 维护数据源、查询模板并执行查询：18 个端点（Q01—Q18）同路径转发 Platform
 * <li>Platform 由 Loopback Simulator 承担，PLT-I02 授权与 Owner 调用记录在同一 Simulator。</li>
 * <li>Platform 的 400/404/409 业务拒绝、专有 Code、422 字段错误（含文案）与 503 DATA_SOURCE_UNAVAILABLE 原样到达浏览器，Request ID 为浏览器请求的。</li>
 *
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryManagementController#listDataSources(com.paytech.operations.console.bff.service.model.OperatorContext, String, String, String, Integer, Integer)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryManagementController#getDataSource(com.paytech.operations.console.bff.service.model.OperatorContext, String)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryManagementController#createDataSource(com.paytech.operations.console.bff.service.model.OperatorContext, com.paytech.operations.console.bff.service.model.dataquery.request.CreateDataSourceRequestBody)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryManagementController#updateDataSource(com.paytech.operations.console.bff.service.model.OperatorContext, String, com.paytech.operations.console.bff.service.model.dataquery.request.UpdateDataSourceRequestBody)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryManagementController#testDataSourceConnection(com.paytech.operations.console.bff.service.model.OperatorContext, com.paytech.operations.console.bff.service.model.dataquery.request.TestDataSourceConnectionRequestBody)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryManagementController#changeDataSourceStatus(com.paytech.operations.console.bff.service.model.OperatorContext, String, com.paytech.operations.console.bff.service.model.dataquery.request.ChangeDataSourceStatusRequestBody)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryManagementController#deleteDataSource(com.paytech.operations.console.bff.service.model.OperatorContext, String, Integer)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryManagementController#listQueryTemplates(com.paytech.operations.console.bff.service.model.OperatorContext, String, String, String, Integer, Integer)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryManagementController#getQueryTemplate(com.paytech.operations.console.bff.service.model.OperatorContext, String)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryManagementController#createQueryTemplate(com.paytech.operations.console.bff.service.model.OperatorContext, com.paytech.operations.console.bff.service.model.dataquery.request.CreateQueryTemplateRequestBody)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryManagementController#updateQueryTemplate(com.paytech.operations.console.bff.service.model.OperatorContext, String, com.paytech.operations.console.bff.service.model.dataquery.request.UpdateQueryTemplateRequestBody)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryManagementController#changeQueryTemplateStatus(com.paytech.operations.console.bff.service.model.OperatorContext, String, com.paytech.operations.console.bff.service.model.dataquery.request.ChangeQueryTemplateStatusRequestBody)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryManagementController#listQueryTemplateVersions(com.paytech.operations.console.bff.service.model.OperatorContext, String, Integer, Integer)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryManagementController#getQueryTemplateVersion(com.paytech.operations.console.bff.service.model.OperatorContext, String, Integer)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryManagementController#restoreQueryTemplate(com.paytech.operations.console.bff.service.model.OperatorContext, String, com.paytech.operations.console.bff.service.model.dataquery.request.RestoreQueryTemplateRequestBody)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryManagementController#validateQueryTemplate(com.paytech.operations.console.bff.service.model.OperatorContext, com.paytech.operations.console.bff.service.model.dataquery.request.ValidateQueryTemplateRequestBody)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryManagementController#trialQueryTemplate(com.paytech.operations.console.bff.service.model.OperatorContext, com.paytech.operations.console.bff.service.model.dataquery.request.TrialQueryTemplateRequestBody)
 * @see com.paytech.operations.console.bff.ext.rest.DataQueryExecutionController#executeDataQuery(com.paytech.operations.console.bff.service.model.OperatorContext, String, com.paytech.operations.console.bff.service.model.dataquery.request.ExecuteDataQueryRequestBody)
 * @author Claude
 */
class DataQueryBrowserAutoTest extends FinanceBrowserAutoTestBase {
    private static final String BOUNDARY = "/data-query-boundary.json";
    private static final String OWNER_REQUEST_ID = "platform-data-query-request";
    private static final String CALLER_SERVICE = "operations-console-bff";
    private static final String PASSWORD_CANARY = "data-source-password-canary";
    private static final String FALLBACK_MESSAGE = "请检查此处配置";

    static Stream<JsonNode> endpoints() {
        return rows(BOUNDARY);
    }

    /** 方法、同路径、Query、Body 与操作人 Header 准确转发一次，响应原样返回；只有执行带调用方 Header。 */
    @ParameterizedTest(name = "{index} 数据查询转发")
    @MethodSource("endpoints")
    void test_normalFlow(JsonNode row) throws Exception {
        platform.rawResponse(ownerPath(row), JSON.writeValueAsString(row.path("response")));

        var response = send(row);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(row.path("response"), JSON.readTree(response.body()));
        var call = ownerCall();
        boolean execution = row.path("path").asString().startsWith("/data-query/v1/");
        assertAll(() -> assertEquals(row.path("method").asString(), call.method()),
            () -> assertEquals(ownerPath(row), call.path()),
            () -> assertEquals(query(row.path("query").asString()), query(call.query())),
            () -> assertEquals(REQUEST_ID, call.header("X-Request-Id")),
            () -> assertEquals(ACCOUNT_ID, call.header("X-Gateway-Actor-Subject")),
            () -> assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id")),
            () -> assertEquals(execution ? CALLER_SERVICE : null,
                call.header(DataQueryExecutionApi.CALLER_SERVICE_HEADER)),
            () -> assertNull(call.header("Authorization")), () -> assertNull(call.header("Cookie")),
            () -> assertNull(call.header("Idempotency-Key")));
        if (row.path("request").isNull()) {
            assertTrue(call.body().isEmpty(), "无 Body 的请求不得转发 Body");
        } else {
            assertEquals(row.path("request"), JSON.readTree(call.body()));
        }
    }

    /** 当前会话授权失败时只调用 PLT-I02，不访问 Platform 数据查询接口。 */
    @ParameterizedTest(name = "{index} 授权拒绝")
    @MethodSource("endpoints")
    void test_authorizationDenied(JsonNode row) throws Exception {
        platform.allow(false);

        var response = send(row);

        assertEquals(403, response.statusCode(), response.body());
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
    }

    /**
     * Platform 业务拒绝、并发冲突、不存在、无权限与数据查询专有 Code 保留状态、Code 与原因，使用浏览器 Request ID
     * <li>DELETE 与 GET 没有 Body，同样保留原因。</li>
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Q03|400|BUSINESS_CHECK_FAILED|数据源编码已存在（含已删除）: RECON_REPLICA",
        "Q04|400|BUSINESS_CHECK_FAILED|连接检查未通过，原配置继续生效：含写权限 INSERT",
        "Q04|409|CONCURRENT_REQUEST|数据源已被修改: 7312345678901234567",
        "Q06|400|BUSINESS_CHECK_FAILED|启用检查未通过：连接失败: Connection refused",
        "Q07|400|BUSINESS_CHECK_FAILED|数据源仍被 ACTIVE 模板引用: ORDER_LOOKUP",
        "Q09|404|NOT_FOUND|模板不存在: 7312345678901230001",
        "Q12|400|BUSINESS_CHECK_FAILED|当前版本的数据源未启用: RECON_REPLICA",
        "Q01|403|OPERATIONS_PERMISSION_DENIED|无权限",
        "Q17|400|DATA_QUERY_RESULT_TOO_LARGE|查询结果过大，请缩小查询范围或减少返回列",
        "Q18|400|DATA_QUERY_TIMEOUT|查询超过 10 秒被取消，请缩小查询范围",
        "Q18|400|BUSINESS_CHECK_FAILED|模板未启用: ORDER_LOOKUP"})
    void test_ownerRejectionPreserved(String id, int status, String code, String detail) throws Exception {
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

    /** Platform 503 DATA_SOURCE_UNAVAILABLE 保留该 Code 与安全提示，不外露 Platform 原文；试运行与执行同样适用。 */
    @ParameterizedTest
    @CsvSource({"Q05", "Q17", "Q18"})
    void test_dataSourceUnavailablePreserved(String id) throws Exception {
        var row = row(BOUNDARY, id);
        platform.rawResponse(ownerPath(row), 503, "application/problem+json",
            problem(503, DataQueryErrorCode.DATA_SOURCE_UNAVAILABLE.getCode(), "platform-private-pool-detail", null),
            Map.of("X-Request-Id", OWNER_REQUEST_ID));

        var response = send(row);

        assertEquals(503, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(DataQueryErrorCode.DATA_SOURCE_UNAVAILABLE.getCode(), body.path("code").asString()),
            () -> assertEquals(DataQueryErrorCode.DATA_SOURCE_UNAVAILABLE.getMessage(), body.path("detail").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()),
            () -> assertFalse(response.body().contains("platform-private-pool-detail")));
        ownerCall();
    }

    /** Platform 其他 503 仍映射为依赖不可用。 */
    @Test
    void test_ownerUnavailable() throws Exception {
        var row = row(BOUNDARY, "Q18");
        platform.problem(ownerPath(row), 503, "SERVICE_UNAVAILABLE");

        var response = send(row);

        assertEquals(503, response.statusCode(), response.body());
        assertEquals(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE.getCode(),
            JSON.readTree(response.body()).path("code").asString());
        ownerCall();
    }

    /** 模板保存 422：本次 Body 内的路径保留 Platform 文案（同一字段多条全部保留，按字段排序输出），越界下标与未知字段丢弃。 */
    @Test
    void test_templateValidationErrorsKeepPlatformMessages() throws Exception {
        var row = row(BOUNDARY, "Q10");
        String errors = """
            [{"field":"body","message":"第 2 行第 14 列：引用了未声明的参数 status"},
             {"field":"body","message":"第 3 行第 1 列：正文不能带顶层 LIMIT"},
             {"field":"parameters[1].allowedValues","message":"ENUM 参数必须声明 allowedValues"},
             {"field":"constraints.maxPageSize","message":"maxPageSize 不能超过 maxRows"},
             {"field":"columns[9].name","message":"remote-out-of-range"},
             {"field":"privateField","message":"remote-unknown"}]
            """;
        platform.rawResponse(ownerPath(row), 422, "application/problem+json",
            problem(422, CommonErrorCode.VALIDATION_ERROR.getCode(), "模板校验未通过", errors),
            Map.of("X-Request-Id", OWNER_REQUEST_ID));

        var response = send(row);

        assertEquals(422, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(CommonErrorCode.VALIDATION_ERROR.getCode(), body.path("code").asString()),
            () -> assertEquals(JSON.readTree("""
                [{"field":"body","message":"第 2 行第 14 列：引用了未声明的参数 status"},
                 {"field":"body","message":"第 3 行第 1 列：正文不能带顶层 LIMIT"},
                 {"field":"constraints.maxPageSize","message":"maxPageSize 不能超过 maxRows"},
                 {"field":"parameters[1].allowedValues","message":"ENUM 参数必须声明 allowedValues"}]
                """), body.path("errors")),
            () -> assertFalse(response.body().contains("remote-")));
        ownerCall();
    }

    /** 执行 422：参数路径落在 params 内保留文案，缺少的必填参数也可定位；不安全文案替换为统一提示。 */
    @Test
    void test_executionParameterErrorsProjected() throws Exception {
        var row = row(BOUNDARY, "Q18");
        String errors = """
            [{"field":"params.statuses[0]","message":"取值不在 allowedValues 中"},
             {"field":"params.startDate","message":"缺少必填参数"},
             {"field":"params.merchantId","message":"bad\\u0007message"},
             {"field":"params.statuses[3]","message":"remote-out-of-range"},
             {"field":"other.nested","message":"remote-unknown"}]
            """;
        platform.rawResponse(ownerPath(row), 422, "application/problem+json",
            problem(422, CommonErrorCode.VALIDATION_ERROR.getCode(), "参数不合法", errors),
            Map.of("X-Request-Id", OWNER_REQUEST_ID));

        var response = send(row);

        assertEquals(422, response.statusCode(), response.body());
        assertEquals(JSON.readTree("""
            [{"field":"params.merchantId","message":"%s"},
             {"field":"params.startDate","message":"缺少必填参数"},
             {"field":"params.statuses[0]","message":"取值不在 allowedValues 中"}]
            """.formatted(FALLBACK_MESSAGE)), JSON.readTree(response.body()).path("errors"));
        ownerCall();
    }

    /** 未知或非法 Query、查询带 Body、非法路径、未知或强转字段、缺少必填字段与超长 Body 均在访问 Platform 前拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        var list = row(BOUNDARY, "Q01");
        assertRejectedBeforePlatform(400, send(list, "?unknown=1", JSON.nullNode(), true));
        assertRejectedBeforePlatform(400, send(list, "?status=DELETED", JSON.nullNode(), true));
        assertRejectedBeforePlatform(400, send(list, "?status=ACTIVE&status=INACTIVE", JSON.nullNode(), true));
        assertRejectedBeforePlatform(400, send(list, "", JSON.createObjectNode(), true));

        var detail = row(BOUNDARY, "Q02");
        assertRejectedBeforePlatform(400, send(at(detail, "/data-query-admin/v1/data-sources/abc"), "", JSON.nullNode(), true));

        var create = row(BOUNDARY, "Q03");
        assertRejectedBeforePlatform(400, send(create, "", with(create, "unknown", "x"), true));
        assertRejectedBeforePlatform(400, send(create, "", with(create, "port", "3306"), true));
        assertRejectedBeforePlatform(422, send(create, "", without(create, "password"), true));
        assertRejectedBeforePlatform(422, send(create, "", with(create, "dataSourceCode", "recon"), true));
        assertRejectedBeforePlatform(422, send(create, "", with(create, "host", "jdbc:mysql://10.0.0.12/x"), true));

        var delete = row(BOUNDARY, "Q07");
        assertRejectedBeforePlatform(400, send(delete, "", JSON.nullNode(), true));
        assertRejectedBeforePlatform(400, send(delete, "?expectedVersion=0", JSON.nullNode(), true));

        var version = row(BOUNDARY, "Q14");
        assertRejectedBeforePlatform(400, send(at(version,
            "/data-query-admin/v1/templates/7312345678901230001/versions/0"), "", JSON.nullNode(), true));

        var validate = row(BOUNDARY, "Q16");
        var oversized = (ObjectNode) validate.path("request").deepCopy();
        oversized.put("body", "x".repeat(70_000));
        assertRejectedBeforePlatform(413, send(validate, "", oversized, true));
        var noConstraints = (ObjectNode) validate.path("request").deepCopy();
        noConstraints.remove("constraints");
        assertRejectedBeforePlatform(422, send(validate, "", noConstraints, true));

        var execute = row(BOUNDARY, "Q18");
        assertRejectedBeforePlatform(400, send(at(execute, "/data-query/v1/templates/order_lookup/executions"), "",
            execute.path("request"), true));
        assertRejectedBeforePlatform(400, send(execute, "", with(execute, "withTotal", "true"), true));
        assertRejectedBeforePlatform(422, send(execute, "", with(execute, "page", 0), true));

        assertEquals(403, send(create, "", create.path("request"), false).statusCode());
        assertTrue(platform.requests().stream().map(RecordedRequest::path).allMatch(AUTHORIZE_PATH::equals));
    }

    /** 分页窗口或身份重复、详情身份不符、写回执的类型、身份或状态与本次操作不符均按上游无效响应 502。 */
    @Test
    void test_invalidUpstreamResponse() throws Exception {
        var dataSources = row(BOUNDARY, "Q01");
        assertUpstreamInvalid(dataSources, withResponse(dataSources, "size", 10));
        assertUpstreamInvalid(dataSources, withResponse(dataSources, "total", 1));
        assertUpstreamInvalid(dataSources, withItem(dataSources, 1, "dataSourceId", "7312345678901234567"));

        var detail = row(BOUNDARY, "Q02");
        assertUpstreamInvalid(detail, withResponse(detail, "dataSourceId", "7312345678901234568"));

        var create = row(BOUNDARY, "Q03");
        assertUpstreamInvalid(create, withResponse(create, "objectType", "QUERY_TEMPLATE"));
        assertUpstreamInvalid(create, withResponse(create, "objectCode", "OTHER_SOURCE"));
        assertUpstreamInvalid(create, withResponse(create, "status", "ACTIVE"));
        assertUpstreamInvalid(create, withResponse(create, "objectId", "0"));

        var update = row(BOUNDARY, "Q04");
        assertUpstreamInvalid(update, withResponse(update, "objectId", "7312345678901234568"));
        var status = row(BOUNDARY, "Q06");
        assertUpstreamInvalid(status, withResponse(status, "status", "INACTIVE"));
        var delete = row(BOUNDARY, "Q07");
        assertUpstreamInvalid(delete, withResponse(delete, "status", "INACTIVE"));

        var templates = row(BOUNDARY, "Q08");
        assertUpstreamInvalid(templates, withResponse(templates, "page", 1));
        var template = row(BOUNDARY, "Q09");
        assertUpstreamInvalid(template, withResponse(template, "templateId", "7312345678901230002"));
        var mismatchedVersion = (ObjectNode) template.path("response").deepCopy();
        ((ObjectNode) mismatchedVersion.path("currentVersion")).put("versionNo", 2);
        assertUpstreamInvalid(template, mismatchedVersion);

        var createTemplate = row(BOUNDARY, "Q10");
        assertUpstreamInvalid(createTemplate, withResponse(createTemplate, "templateCode", "OTHER_TEMPLATE"));
        var updateTemplate = row(BOUNDARY, "Q11");
        assertUpstreamInvalid(updateTemplate, withResponse(updateTemplate, "templateId", "7312345678901230002"));
        var templateStatus = row(BOUNDARY, "Q12");
        assertUpstreamInvalid(templateStatus, withResponse(templateStatus, "objectType", "DATA_SOURCE"));

        var versions = row(BOUNDARY, "Q13");
        assertUpstreamInvalid(versions, withItem(versions, 1, "versionNo", 3));
        var version = row(BOUNDARY, "Q14");
        assertUpstreamInvalid(version, withResponse(version, "versionNo", 2));
        var restore = row(BOUNDARY, "Q15");
        assertUpstreamInvalid(restore, withResponse(restore, "templateId", "7312345678901230002"));
    }

    /** Platform 返回合同外字段（如 Vault Token）时 BFF 不投影该字段。 */
    @Test
    void test_unknownUpstreamFieldNotProjected() throws Exception {
        var detail = row(BOUNDARY, "Q02");
        platform.rawResponse(ownerPath(detail),
            JSON.writeValueAsString(withResponse(detail, "passwordToken", "vault-token-canary")));

        var response = send(detail);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(detail.path("response"), JSON.readTree(response.body()));
        assertFalse(response.body().contains("vault-token-canary"));
    }

    /** 数据源密码只在请求中转发给 Platform，不出现在回执里。 */
    @Test
    void test_passwordOnlyForwarded() throws Exception {
        var create = row(BOUNDARY, "Q03");
        platform.rawResponse(ownerPath(create), JSON.writeValueAsString(create.path("response")));

        var response = send(create);

        assertEquals(200, response.statusCode(), response.body());
        assertFalse(response.body().contains(PASSWORD_CANARY));
        assertEquals(PASSWORD_CANARY, JSON.readTree(ownerCall().body()).path("password").asString());
    }

    /** Platform 严格 Problem 响应体；errors 为 JSON 数组文本或 null。 */
    private static String problem(int status, String code, String detail, String errors) {
        var body = JSON.createObjectNode().put("type", "about:blank")
            .put("title", HttpStatus.valueOf(status).getReasonPhrase()).put("detail", detail)
            .put("code", code).put("requestId", OWNER_REQUEST_ID).put("timestamp", "2026-10-22T00:00:00.123456Z");
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
