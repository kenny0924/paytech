package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ACCOUNT_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.AUTHORIZE_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.UPSTREAM_DETAIL_CANARY;
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

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.operations.console.bff.ext.rest.CurrencySpecManagementController;
import com.paytech.operations.console.bff.ext.rest.FinanceSetupController;
import com.paytech.operations.console.bff.test.base.FinanceBrowserAutoTestBase;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RecordedRequest;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 与 HttpExchange 读取和维护 Platform 币种规则：币种页 4 个端点（C01—C04）与财务页币种下拉（R16）
 * <li>Platform 由 Loopback Simulator 承担，PLT-I02 授权与 Owner 调用记录在同一 Simulator；ledger 不再参与币种读取。</li>
 * <li>币种页列表与财务页下拉都读参考数据币种接口（ownerResponse 为 Platform 形状，response 为页面形状）；财务页下拉的状态筛选交给 Platform。</li>
 *
 * @see CurrencySpecManagementController#listCurrencySpecs()
 * @see CurrencySpecManagementController#createCurrencySpec(com.paytech.operations.console.bff.service.model.OperatorContext,
 *      com.paytech.operations.console.bff.service.model.CurrencySpecManagementModels.CreateCurrencySpecRequestBody)
 * @see CurrencySpecManagementController#updateCurrencySpec(com.paytech.operations.console.bff.service.model.OperatorContext,
 *      String, com.paytech.operations.console.bff.service.model.CurrencySpecManagementModels.UpdateCurrencySpecRequestBody)
 * @see CurrencySpecManagementController#changeCurrencySpecStatus(com.paytech.operations.console.bff.service.model.OperatorContext,
 *      String, com.paytech.operations.console.bff.service.model.CurrencySpecManagementModels.ChangeCurrencySpecStatusRequestBody)
 * @see FinanceSetupController#listCurrencies(jakarta.servlet.http.HttpServletRequest, String)
 * @author Claude
 */
class CurrencySpecBrowserAutoTest extends FinanceBrowserAutoTestBase {
    private static final String BOUNDARY = "/currency-spec-boundary.json";
    private static final String FINANCE_CURRENCIES = "/finance/currencies/v1";
    private static final String FINANCE_CURRENCY_ROUTE = "operations-finance-currency-list";
    private static final String OWNER_REQUEST_ID = "platform-currency-request";
    /** 参考数据币种接口：财务页下拉的数据源。 */
    private static final String REFERENCE_PATH = "/platform/v1/reference-data/currencies";
    /** Platform 参考数据币种（按币种代码升序，三种状态各一条），财务页筛选时由 Platform 只返回对应状态的条目。 */
    private static final List<String> REFERENCE_ITEMS = List.of("""
        {"currencyCode":"AED","numericCode":"784","name":"UAE Dirham","symbol":"د.إ","minorUnit":2,"roundingMode":"HALF_UP",\
        "logoFileAddress":"/files/v1/7312345678901234001","logoPublicUrl":"https://static.local.test/public/aed.svg",\
        "status":"DRAFT","version":1}""", """
        {"currencyCode":"JPY","numericCode":null,"name":null,"symbol":null,"minorUnit":0,"roundingMode":"HALF_UP",\
        "logoFileAddress":null,"logoPublicUrl":null,"status":"INACTIVE","version":3}""", """
        {"currencyCode":"USD","numericCode":"840","name":"US Dollar","symbol":"$","minorUnit":2,"roundingMode":"HALF_UP",\
        "status":"ACTIVE","version":2}""");

    static Stream<JsonNode> endpoints() {
        return rows(BOUNDARY);
    }

    /** 方法、Owner 路径、Body 与操作人 Header 准确转发一次，响应原样返回；查询不携带操作人。 */
    @ParameterizedTest(name = "{index} 币种转发")
    @MethodSource("endpoints")
    void test_normalFlow(JsonNode row) throws Exception {
        platform.rawResponse(ownerPath(row), JSON.writeValueAsString(ownerResponse(row)));

        var response = send(row);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(row.path("response"), JSON.readTree(response.body()));
        var call = ownerCall();
        boolean command = !"GET".equals(row.path("method").asString());
        assertAll(() -> assertEquals(row.path("method").asString(), call.method()),
            () -> assertNull(call.query()),
            () -> assertEquals(REQUEST_ID, call.header("X-Request-Id")),
            () -> assertEquals(command ? ACCOUNT_ID : null, call.header("X-Gateway-Actor-Subject")),
            () -> assertEquals(command ? SESSION_ID : null, call.header("X-Gateway-Session-Id")),
            () -> assertNull(call.header("Authorization")), () -> assertNull(call.header("Cookie")),
            () -> assertNull(call.header("Idempotency-Key")));
        if (command) {
            assertEquals(row.path("request"), JSON.readTree(call.body()));
        } else {
            assertTrue(call.body().isEmpty(), "查询不得转发 Body");
        }
    }

    /** 当前会话授权失败时只调用 PLT-I02，不访问币种接口。 */
    @ParameterizedTest(name = "{index} 授权拒绝")
    @MethodSource("endpoints")
    void test_authorizationDenied(JsonNode row) throws Exception {
        platform.allow(false);

        var response = send(row);

        assertEquals(403, response.statusCode(), response.body());
        assertOnlyAuthorized();
    }

    /** 财务页币种下拉读参考数据币种接口，status 原样作为 Platform 筛选参数（省略时不带），按 Platform 顺序投影，响应形态不含版本，ledger 零调用。 */
    @ParameterizedTest
    @CsvSource(value = {"ACTIVE,USD", "DRAFT,AED", "INACTIVE,JPY", "'',AED|JPY|USD"})
    void test_financeCurrenciesFilteredByPlatform(String status, String expectedCurrencies) throws Exception {
        var expected = List.of(expectedCurrencies.split("\\|"));
        platform.rawResponse(REFERENCE_PATH, referenceList(expected));

        var response = send(financeCurrencies(), status.isEmpty() ? "" : "?status=" + status, JSON.nullNode(), true);

        assertEquals(200, response.statusCode(), response.body());
        var items = JSON.readTree(response.body()).path("items");
        assertEquals(expected, items.valueStream().map(item -> item.path("currency").asString()).toList());
        items.forEach(item -> assertEquals(List.of("currency", "minorUnit", "roundingMode", "status"),
            item.propertyNames().stream().toList()));
        var call = ownerCall();
        assertEquals(REFERENCE_PATH, call.path());
        assertEquals(status.isEmpty() ? null : "status=" + status, call.query());
        assertTrue(ledger.requests().isEmpty(), ledger.requests().toString());
    }

    /** 财务页下拉：Platform 返回条目与筛选状态不符、版本缺失、未知状态、越界小数位或重复币种均按上游无效响应 502。 */
    @Test
    void test_financeCurrenciesInvalidUpstreamResponse() throws Exception {
        var finance = financeCurrencies();
        assertFinanceUpstreamInvalid(finance, "?status=ACTIVE", referenceList(List.of("AED")));
        assertFinanceUpstreamInvalid(finance, "", referenceItem("USD", "version", null));
        assertFinanceUpstreamInvalid(finance, "", referenceItem("USD", "status", "ENABLED"));
        assertFinanceUpstreamInvalid(finance, "", referenceItem("USD", "minorUnit", 9));
        assertFinanceUpstreamInvalid(finance, "", referenceList(List.of("USD", "USD")));
    }

    /** 已启用币种只更换图标：标准属性按当前值原样提交，回执状态仍为 ACTIVE 时正常返回。 */
    @Test
    void test_activeCurrencyLogoChange() throws Exception {
        var update = row(BOUNDARY, "C03");
        var request = with(update, "logoFileAddress", "/files/v1/7312345678901234002");
        platform.rawResponse(ownerPath(update), JSON.writeValueAsString(withResponse(update, "status",
            CurrencySpecStatus.ACTIVE.getCode())));

        var response = send(update, "", request, true);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(CurrencySpecStatus.ACTIVE.getCode(), JSON.readTree(response.body()).path("status").asString());
        assertEquals(request, JSON.readTree(ownerCall().body()));
    }

    /** 财务页筛选值非法时在访问 Platform 前拒绝。 */
    @Test
    void test_financeCurrenciesInvalidStatus() throws Exception {
        var response = send(financeCurrencies(), "?status=ENABLED", JSON.nullNode(), true);

        assertEquals(400, response.statusCode(), response.body());
        assertOnlyAuthorized();
    }

    /** 列表带 Query 或 Body、未知或缺失 Body 字段、类型强转、越界小数位、小写或非法币种路径均在访问 Platform 前拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        var list = row(BOUNDARY, "C01");
        assertRejectedBeforePlatform(400, send(list, "?status=ACTIVE", JSON.nullNode(), true));
        assertRejectedBeforePlatform(400, send(list, "", JSON.createObjectNode(), true));

        var create = row(BOUNDARY, "C02");
        assertRejectedBeforePlatform(400, send(create, "", with(create, "status", "ACTIVE"), true));
        assertRejectedBeforePlatform(400, send(create, "", with(create, "minorUnit", "2"), true));
        assertRejectedBeforePlatform(422, send(create, "", with(create, "minorUnit", 9), true));
        assertRejectedBeforePlatform(422, send(create, "", with(create, "currency", "aed"), true));
        assertRejectedBeforePlatform(422, send(create, "", with(create, "roundingMode", "HALF_EVEN"), true));
        assertRejectedBeforePlatform(422, send(create, "", with(create, "reason", " "), true));
        assertRejectedBeforePlatform(422, send(create, "", with(create, "numericCode", "84"), true));
        assertRejectedBeforePlatform(422, send(create, "", with(create, "name", " "), true));
        assertRejectedBeforePlatform(422, send(create, "", with(create, "symbol", "123456789"), true));
        assertRejectedBeforePlatform(422, send(create, "", with(create, "logoFileAddress", "https://static.local.test/aed.svg"), true));

        var update = row(BOUNDARY, "C03");
        var missingVersion = (ObjectNode) update.path("request").deepCopy();
        missingVersion.remove("expectedVersion");
        assertRejectedBeforePlatform(422, send(update, "", missingVersion, true));
        var lowerCase = update.deepCopy();
        ((ObjectNode) lowerCase).put("path", "/currency-admin/v1/currency-specs/aed");
        assertRejectedBeforePlatform(400, send(lowerCase, "", update.path("request"), true));

        var status = row(BOUNDARY, "C04");
        assertRejectedBeforePlatform(422, send(status, "", with(status, "status", "DRAFT"), true));
        assertRejectedBeforePlatform(422, send(status, "", with(status, "expectedVersion", 0), true));

        assertEquals(403, send(create, "", create.path("request"), false).statusCode());
        assertTrue(platform.requests().stream().map(RecordedRequest::path).allMatch(AUTHORIZE_PATH::equals));
    }

    /** 写操作的业务拒绝保留 Platform 状态、公共 Code 与原因（如币种已存在、已启用不可修改），使用浏览器 Request ID。 */
    @ParameterizedTest
    @CsvSource({"C02,400,BUSINESS_CHECK_FAILED,币种规则已存在: AED",
        "C03,400,BUSINESS_CHECK_FAILED,币种规则已启用过，小数位与舍入方式不可修改: AED",
        "C03,404,NOT_FOUND,币种规则不存在: AED", "C03,409,CONCURRENT_REQUEST,币种规则已被修改: AED",
        "C04,400,BUSINESS_CHECK_FAILED,币种规则状态不能从 ACTIVE 变为 ACTIVE"})
    void test_ownerRejectionShowsPlatformReason(String id, int status, String code, String detail) throws Exception {
        var row = row(BOUNDARY, id);
        platform.rawResponse(ownerPath(row), status, "application/problem+json", problem(status, code, detail),
            Map.of("X-Request-Id", OWNER_REQUEST_ID));

        var response = send(row);

        assertEquals(status, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(code, body.path("code").asString()),
            () -> assertEquals(detail, body.path("detail").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()));
        ownerCall();
    }

    /** Platform 503 映射为依赖不可用，不外露 Platform 原文。 */
    @ParameterizedTest
    @CsvSource({"C01", "C02"})
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

    /** Platform 422 只保留本次请求体内存在的字段路径，不传播远端文案。 */
    @Test
    void test_ownerValidationPathsProjected() throws Exception {
        var row = row(BOUNDARY, "C02");
        String upstream = """
            {"type":"about:blank","title":"Unprocessable Content","detail":"remote-private-detail","code":"VALIDATION_ERROR",
             "requestId":"platform-fields-request","timestamp":"2026-10-20T00:00:00.123456Z","errors":[
              {"field":"minorUnit","message":"remote-private-message"},{"field":"privateField","message":"unknown"}]}
            """;
        platform.rawResponse(ownerPath(row), 422, "application/problem+json", upstream,
            Map.of("X-Request-Id", "platform-fields-request"));

        var response = send(row);

        assertEquals(422, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(CommonErrorCode.VALIDATION_ERROR.getCode(), body.path("code").asString()),
            () -> assertEquals(1, body.path("errors").size()),
            () -> assertEquals("minorUnit", body.path("errors").get(0).path("field").asString()),
            () -> assertFalse(response.body().contains("remote-private")),
            () -> assertFalse(response.body().contains("privateField")));
    }

    /** 修改被拒（Platform 422 VALIDATION_ERROR）：冻结字段与非公开图标的字段路径投影到页面请求体，顺序不依赖 Platform，远端文案不外露。 */
    @Test
    void test_updateFrozenFieldAndLogoRejectionsProjected() throws Exception {
        var row = row(BOUNDARY, "C03");
        String upstream = """
            {"type":"about:blank","title":"Unprocessable Content","detail":"remote-private-detail","code":"VALIDATION_ERROR",
             "requestId":"platform-fields-request","timestamp":"2026-10-27T00:00:00.123456Z","errors":[
              {"field":"symbol","message":"remote-private AED ACTIVE"},{"field":"logoFileAddress","message":"remote-private not public"},
              {"field":"minorUnit","message":"remote-private AED ACTIVE"}]}
            """;
        platform.rawResponse(ownerPath(row), 422, "application/problem+json", upstream, Map.of("X-Request-Id", "platform-fields-request"));

        var response = send(row, "", with(row, "logoFileAddress", "/files/v1/7312345678901234003"), true);

        assertEquals(422, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(CommonErrorCode.VALIDATION_ERROR.getCode(), body.path("code").asString()),
            () -> assertEquals(List.of("logoFileAddress", "minorUnit", "symbol"),
                body.path("errors").valueStream().map(e -> e.path("field").asString()).sorted().toList()),
            () -> assertFalse(response.body().contains("remote-private")));
        ownerCall();
    }

    /** 列表未知状态、越界小数位、重复币种、非法数字代码或图标地址，以及写回执的类型、币种或状态与本次操作不符均按上游无效响应 502。 */
    @Test
    void test_invalidUpstreamResponse() throws Exception {
        var list = row(BOUNDARY, "C01");
        assertUpstreamInvalid(list, withItem(list, 0, "status", "ENABLED"));
        assertUpstreamInvalid(list, withItem(list, 0, "minorUnit", 9));
        assertUpstreamInvalid(list, withItem(list, 0, "roundingMode", "HALF_EVEN"));
        assertUpstreamInvalid(list, withItem(list, 1, "currencyCode", "AED"));
        assertUpstreamInvalid(list, withItem(list, 2, "version", null));
        assertUpstreamInvalid(list, withItem(list, 2, "numericCode", "84"));
        assertUpstreamInvalid(list, withItem(list, 0, "logoFileAddress", "https://static.local.test/aed.svg"));

        var create = row(BOUNDARY, "C02");
        assertUpstreamInvalid(create, withResponse(create, "objectType", "PERMISSION"));
        assertUpstreamInvalid(create, withResponse(create, "objectCode", "USD"));
        assertUpstreamInvalid(create, withResponse(create, "status", CurrencySpecStatus.ACTIVE.getCode()));
        var status = row(BOUNDARY, "C04");
        assertUpstreamInvalid(status, withResponse(status, "status", CurrencySpecStatus.INACTIVE.getCode()));
        assertUpstreamInvalid(status, withResponse(status, "objectId", "0"));
        var update = row(BOUNDARY, "C03");
        assertUpstreamInvalid(update, withResponse(update, "status", "ENABLED"));
        assertUpstreamInvalid(update, withResponse(update, "objectCode", "USD"));
    }

    /** Platform 返回合同外字段时 BFF 不投影该字段。 */
    @Test
    void test_unknownUpstreamFieldNotProjected() throws Exception {
        var list = row(BOUNDARY, "C01");
        platform.rawResponse(ownerPath(list), JSON.writeValueAsString(withItem(list, 0, "internalNote", "OWNER-ONLY-CANARY")));

        var response = send(list);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(list.path("response"), JSON.readTree(response.body()));
    }

    /** Platform 严格 Problem 响应体。 */
    private static String problem(int status, String code, String detail) {
        return JSON.writeValueAsString(JSON.createObjectNode().put("type", "about:blank")
            .put("title", org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase()).put("detail", detail)
            .put("code", code).put("requestId", OWNER_REQUEST_ID).put("timestamp", "2026-10-20T00:00:00.123456Z"));
    }

    /** Platform 参考数据币种列表：按给定币种代码取样例条目。 */
    private static String referenceList(List<String> currencies) {
        var items = JSON.createArrayNode();
        currencies.forEach(currency -> items.add(referenceItemNode(currency)));
        return JSON.writeValueAsString(JSON.createObjectNode().set("items", items));
    }

    /** 只含一条币种的 Platform 列表，并覆盖该条目的一个字段。 */
    private static String referenceItem(String currency, String field, Object value) {
        var items = JSON.createArrayNode().add(put(referenceItemNode(currency), field, value));
        return JSON.writeValueAsString(JSON.createObjectNode().set("items", items));
    }

    /** 样例中的单条 Platform 参考数据币种。 */
    private static ObjectNode referenceItemNode(String currency) {
        return (ObjectNode) REFERENCE_ITEMS.stream().map(JSON::readTree)
            .filter(item -> currency.equals(item.path("currencyCode").asString())).findFirst().orElseThrow();
    }

    /** 财务页下拉收到无效 Platform 响应时 502，且本次只调用一次 Owner。 */
    private void assertFinanceUpstreamInvalid(JsonNode finance, String query, String upstream) throws Exception {
        resetBetweenCalls();
        platform.rawResponse(REFERENCE_PATH, upstream);
        var response = send(finance, query, JSON.nullNode(), true);
        assertEquals(502, response.statusCode(), response.body());
        ownerCall();
    }

    /** 财务页币种下拉的浏览器请求坐标。 */
    private static JsonNode financeCurrencies() {
        return JSON.createObjectNode().put("method", "GET").put("path", FINANCE_CURRENCIES)
            .put("routeCode", FINANCE_CURRENCY_ROUTE);
    }

    /** Platform 响应 502 且本次只调用一次 Owner。 */
    private void assertUpstreamInvalid(JsonNode row, JsonNode upstream) throws Exception {
        resetBetweenCalls();
        platform.rawResponse(ownerPath(row), JSON.writeValueAsString(upstream));
        var response = send(row);
        assertEquals(502, response.statusCode(), response.body());
        ownerCall();
    }

    /** 本地拒绝：指定状态且 Platform 至多收到授权请求（Query 边界先于授权拒绝）；之后为下一次调用重置记录。 */
    private void assertRejectedBeforePlatform(int status, HttpResponse<String> response) {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(platform.requests().stream().map(RecordedRequest::path).allMatch(AUTHORIZE_PATH::equals),
            platform.requests().toString());
        resetBetweenCalls();
    }

    /** Platform 只收到一次 PLT-I02 授权。 */
    private void assertOnlyAuthorized() {
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
    }

    /** 授权之后恰好一次 Owner 调用，返回该调用。 */
    private RecordedRequest ownerCall() {
        var requests = platform.requests();
        assertEquals(2, requests.size(), requests.toString());
        assertEquals(AUTHORIZE_PATH, requests.getFirst().path());
        return requests.getLast();
    }

    /** 复制样例请求并覆盖一个字段。 */
    private static ObjectNode with(JsonNode row, String field, Object value) {
        return put((ObjectNode) row.path("request").deepCopy(), field, value);
    }

    /** 复制样例响应并覆盖一个字段。 */
    private static ObjectNode withResponse(JsonNode row, String field, Object value) {
        return put((ObjectNode) row.path("response").deepCopy(), field, value);
    }

    /** Platform 响应：样例给出 ownerResponse（与页面形状不同）时取之，否则与页面响应相同。 */
    private static JsonNode ownerResponse(JsonNode row) {
        return row.has("ownerResponse") ? row.path("ownerResponse") : row.path("response");
    }

    /** 复制列表的 Platform 响应并覆盖指定条目的一个字段。 */
    private static ObjectNode withItem(JsonNode row, int index, String field, Object value) {
        var copy = (ObjectNode) ownerResponse(row).deepCopy();
        put((ObjectNode) ((ArrayNode) copy.path("items")).get(index), field, value);
        return copy;
    }

    /** 按值类型写入字段；null 写为 JSON null。 */
    private static ObjectNode put(ObjectNode node, String field, Object value) {
        if (value == null) {
            node.putNull(field);
        } else if (value instanceof Integer number) {
            node.put(field, number);
        } else {
            node.put(field, value.toString());
        }
        return node;
    }
}
