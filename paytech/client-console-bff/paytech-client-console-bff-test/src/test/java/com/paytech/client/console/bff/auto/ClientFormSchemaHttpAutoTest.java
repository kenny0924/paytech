package com.paytech.client.console.bff.auto;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.paytech.client.console.bff.ClientConsoleBffApplication;
import com.paytech.client.console.bff.domain.enums.ClientBffErrorCode;
import com.paytech.client.console.bff.domain.enums.ReferenceCatalog;
import com.paytech.client.master.api.onboarding.response.FormSchemaResponseBody;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.api.referencedata.response.CountrySubdivisionListResponseBody;
import com.paytech.platform.support.api.referencedata.response.CountrySubdivisionResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCountryListResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCountryResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCurrencyListResponseBody;
import com.paytech.platform.support.api.referencedata.response.ReferenceCurrencyResponseBody;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * CM-2 采集 Schema 页面（D-04）：真实 Controller、Processor 与出站 Client 经测试 HTTP 接口读取 client-master Schema 与
 * Platform 参考数据 DTO；只配置回执，不替换生产链路。
 */
@ActiveProfiles({ "auto", "onboarding-auto" })
@SpringBootTest(classes = ClientConsoleBffApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClientFormSchemaHttpAutoTest {
    private static final String ORIGIN = "https://client-console.local.test";
    private static final String SCHEMA_PATH = "/client-onboarding/v1/form-schema";
    private static final String REFERENCE = "/platform-support/platform/v1/reference-data";
    private static final String COUNTRIES = REFERENCE + "/countries";
    private static final String CURRENCIES = REFERENCE + "/currencies";
    private static final String SUBDIVISIONS = REFERENCE + "/country-subdivisions";
    private static final String ACTIVE = ReferenceDataStatus.ACTIVE.getCode();
    private static final String CURRENCY = ReferenceCatalog.REF_CURRENCY.getCode();
    private static final String SUBDIVISION = ReferenceCatalog.REF_COUNTRY_SUBDIVISION.getCode();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpServer OWNERS = startOwners();

    /** 外部 DTO Mocker：按路径配置回执（DTO 或故障状态码），记录调用与未配置的意外调用。 */
    private static final Map<String, Object> RESPONSES = new ConcurrentHashMap<>();
    private static final Map<String, Integer> FAILURES = new ConcurrentHashMap<>();
    private static final List<String> CALLS = new CopyOnWriteArrayList<>();
    private static final List<String> UNEXPECTED = new CopyOnWriteArrayList<>();

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + OWNERS.getAddress().getPort();
        registry.add("client.platform-test-base-url", () -> base + "/platform-support");
        registry.add("client.cct-test-base-url", () -> base);
        registry.add("client.risk-test-base-url", () -> base);
    }

    @BeforeEach
    void configureReferenceData() {
        RESPONSES.put(COUNTRIES, countries(country("AE", "United Arab Emirates", "https://cdn.example.test/flags/ae.svg", "971"),
            country("SA", "Saudi Arabia", null, "966")));
        RESPONSES.put(CURRENCIES, currencies(currency("AED", "UAE Dirham", "https://cdn.example.test/currency/aed.svg"),
            currency("USD", "US Dollar", null)));
        RESPONSES.put(SUBDIVISIONS, subdivisions(subdivision("AE-DU", "AE", "Dubai"), subdivision("AE-AZ", "AE", "Abu Dhabi")));
    }

    /** 断言失败后仍检查意外调用并清空回执与记录。 */
    @AfterEach
    void verifyAndClear() {
        var unexpected = new ArrayList<>(UNEXPECTED);
        RESPONSES.clear();
        FAILURES.clear();
        CALLS.clear();
        UNEXPECTED.clear();
        assertEquals(List.of(), unexpected, "unexpected outbound calls");
    }

    @AfterAll
    static void stopOwners() {
        OWNERS.stop(0);
    }

    /** 辖区本地地址引用行政区、业务字段引用币种：补全国家、币种与该辖区的行政区，自有目录不变。 */
    @Test
    void test_normalFlow() throws Exception {
        RESPONSES.put(SCHEMA_PATH, schema("AE", localAddress(SUBDIVISION), genericAddress(null), CURRENCY));

        var result = get("/client-onboarding/v1/form-schema?jurisdiction=AE&partyType=ORGANIZATION");

        assertEquals(200, result.statusCode(), result.body());
        JsonNode page = JSON.readTree(result.body());
        assertEquals("9270000000700001", page.path("schemaId").asString());
        assertEquals("BUSINESS", page.path("sections").path(0).path("section").asString());
        assertEquals("AE_POBOX", page.path("addressFormat").path("code").asString());
        var countries = page.path("countries");
        assertEquals(2, countries.size());
        assertEquals("AE", countries.path(0).path("code").asString());
        assertEquals("United Arab Emirates", countries.path(0).path("name").asString());
        assertEquals("https://cdn.example.test/flags/ae.svg", countries.path(0).path("logoPublicUrl").asString());
        assertEquals("971", countries.path(0).path("callingCode").asString());
        assertTrue(countries.path(1).path("logoPublicUrl").isNull());
        var currencies = page.path("catalogs").path(CURRENCY);
        assertEquals(List.of("AED", "USD"), codes(currencies));
        assertEquals("UAE Dirham", currencies.path(0).path("name").asString());
        assertEquals("https://cdn.example.test/currency/aed.svg", currencies.path(0).path("logoPublicUrl").asString());
        assertTrue(currencies.path(0).path("labelKey").isNull());
        var subdivisions = page.path("catalogs").path(SUBDIVISION);
        assertEquals(List.of("AE-DU", "AE-AZ"), codes(subdivisions));
        assertEquals("AE", subdivisions.path(0).path("countryCode").asString());
        assertEquals("Dubai", subdivisions.path(0).path("name").asString());
        var business = page.path("catalogs").path("BUSINESS_MODEL");
        assertEquals(List.of("SERVICES"), codes(business));
        assertEquals("onboardingSchema.catalog.BUSINESS_MODEL.SERVICES", business.path(0).path("labelKey").asString());
        assertTrue(business.path(0).path("name").isNull());
        // 门槛比较方式与声明文档版本沿用 client-master 合同类型原样返回。
        assertEquals("GT", page.path("relationRules").path("uboThresholdComparison").asString());
        assertEquals("2026-10-draft", page.path("declarations").path(0).path("documentVersion").asString());
        assertEquals(List.of(SCHEMA_PATH, COUNTRIES + "?status=" + ACTIVE, CURRENCIES + "?status=" + ACTIVE,
            SUBDIVISIONS + "?countryCode=AE&status=" + ACTIVE), CALLS);
    }

    /** 通用地址格式也引用行政区：不限定国家，读取全部 ACTIVE 行政区，由页面按地址国家筛选。 */
    @Test
    void test_edgeCase_subdivisionsBeyondLocalAddress() throws Exception {
        RESPONSES.put(SCHEMA_PATH, schema("AE", localAddress(SUBDIVISION), genericAddress(SUBDIVISION), null));
        RESPONSES.put(SUBDIVISIONS, subdivisions(subdivision("AE-DU", "AE", "Dubai"), subdivision("EG-C", "EG", "Cairo")));

        var result = get("/client-onboarding/v1/form-schema?jurisdiction=AE&partyType=ORGANIZATION");

        assertEquals(200, result.statusCode(), result.body());
        var subdivisions = JSON.readTree(result.body()).path("catalogs").path(SUBDIVISION);
        assertEquals(List.of("AE-DU", "EG-C"), codes(subdivisions));
        assertEquals("EG", subdivisions.path(1).path("countryCode").asString());
        assertEquals(List.of(SCHEMA_PATH, COUNTRIES + "?status=" + ACTIVE, SUBDIVISIONS + "?status=" + ACTIVE), CALLS);
    }

    /** 辖区代码不是 ACTIVE 国家（无法确定国家）：同样读取全部行政区。 */
    @Test
    void test_edgeCase_jurisdictionNotCountry() throws Exception {
        RESPONSES.put(SCHEMA_PATH, schema("EG", localAddress(SUBDIVISION), genericAddress(null), null));

        var result = get("/client-onboarding/v1/form-schema?jurisdiction=EG&partyType=INDIVIDUAL");

        assertEquals(200, result.statusCode(), result.body());
        assertEquals(List.of(SCHEMA_PATH, COUNTRIES + "?status=" + ACTIVE, SUBDIVISIONS + "?status=" + ACTIVE), CALLS);
    }

    /** Schema 不引用参考数据目录：只补国家列表，不读取币种与行政区。 */
    @Test
    void test_noReferenceCatalogs() throws Exception {
        RESPONSES.put(SCHEMA_PATH, schema("AE", localAddress(null), genericAddress(null), null));

        var result = get("/client-onboarding/v1/form-schema?applicationId=10");

        assertEquals(200, result.statusCode(), result.body());
        var page = JSON.readTree(result.body());
        assertEquals(2, page.path("countries").size());
        assertFalse(page.path("catalogs").has(CURRENCY));
        assertFalse(page.path("catalogs").has(SUBDIVISION));
        assertEquals(List.of(SCHEMA_PATH, COUNTRIES + "?status=" + ACTIVE), CALLS);
    }

    /**
     * 业务信息补采（REQ-12）回归：client-master 的多选控件、commercial 支付方式外部目录与交付方式目录、天数可见条件与正则原样到达页面；
     * 外部目录不是 REF_ 目录，不触发额外的 Platform 查询
     */
    @Test
    void test_businessInfoFieldsPassThrough() throws Exception {
        var schema = schema("AE", localAddress(null), genericAddress(null), null);
        var methods = new FormSchemaResponseBody.Field();
        methods.setFieldKey("business.expectedPaymentMethods");
        methods.setWidget("MULTI_SELECT");
        methods.setCatalogCode("COMMERCIAL_PAYMENT_METHOD");
        methods.setRequirementLevel("REQUIRED");
        methods.setAppliesTo("PRIMARY");
        methods.setLabelKey("onboardingSchema.field.business.expectedPaymentMethods");
        var days = new FormSchemaResponseBody.Field();
        days.setFieldKey("business.fulfillmentDays");
        days.setWidget("TEXT");
        days.setRequirementLevel("REQUIRED");
        days.setAppliesTo("PRIMARY");
        days.setVisibleWhen("business.fulfillmentType=PRE_ORDER");
        var validation = new FormSchemaResponseBody.Validation();
        validation.setPattern("^(36[0-5]|3[0-5][0-9]|[12][0-9]{2}|[1-9][0-9]?)$");
        validation.setMaxLength(3);
        days.setValidation(validation);
        days.setLabelKey("onboardingSchema.field.business.fulfillmentDays");
        var group = schema.getSections().getFirst().getGroups().getFirst();
        group.setFields(List.of(group.getFields().get(0), group.getFields().get(1), methods, days));
        schema.setCatalogs(Map.of("BUSINESS_MODEL", schema.getCatalogs().get("BUSINESS_MODEL"),
            "COMMERCIAL_PAYMENT_METHOD", List.of(option("APPLE_PAY", "onboardingSchema.paymentMethod.APPLE_PAY"),
                option("CARD", "onboardingSchema.paymentMethod.CARD")),
            "FULFILLMENT_TYPE", List.of(option("INSTANT", "onboardingSchema.catalog.FULFILLMENT_TYPE.INSTANT"),
                option("PRE_ORDER", "onboardingSchema.catalog.FULFILLMENT_TYPE.PRE_ORDER"))));
        RESPONSES.put(SCHEMA_PATH, schema);

        var result = get("/client-onboarding/v1/form-schema?applicationId=10");

        assertEquals(200, result.statusCode(), result.body());
        var page = JSON.readTree(result.body());
        var fields = page.path("sections").path(0).path("groups").path(0).path("fields");
        assertEquals("MULTI_SELECT", fields.path(2).path("widget").asString());
        assertEquals("COMMERCIAL_PAYMENT_METHOD", fields.path(2).path("catalogCode").asString());
        assertEquals("business.fulfillmentType=PRE_ORDER", fields.path(3).path("visibleWhen").asString());
        assertEquals("^(36[0-5]|3[0-5][0-9]|[12][0-9]{2}|[1-9][0-9]?)$", fields.path(3).path("validation").path("pattern").asString());
        var paymentMethods = page.path("catalogs").path("COMMERCIAL_PAYMENT_METHOD");
        assertEquals(List.of("APPLE_PAY", "CARD"), codes(paymentMethods));
        assertEquals("onboardingSchema.paymentMethod.CARD", paymentMethods.path(1).path("labelKey").asString());
        assertEquals(List.of("INSTANT", "PRE_ORDER"), codes(page.path("catalogs").path("FULFILLMENT_TYPE")));
        assertEquals(List.of(SCHEMA_PATH, COUNTRIES + "?status=" + ACTIVE), CALLS);
    }

    /** Platform 任一参考数据不可用：整页返回 Problem，不返回缺少选项的 Schema。 */
    @Test
    void test_platformUnavailable() throws Exception {
        RESPONSES.put(SCHEMA_PATH, schema("AE", localAddress(SUBDIVISION), genericAddress(null), CURRENCY));
        for (String path : List.of(COUNTRIES, CURRENCIES, SUBDIVISIONS)) {
            FAILURES.clear();
            FAILURES.put(path, 503);

            var result = get("/client-onboarding/v1/form-schema?jurisdiction=AE&partyType=ORGANIZATION");

            assertProblem(result, 503, ClientBffErrorCode.DEPENDENCY_UNAVAILABLE.getCode());
            assertFalse(result.body().contains("schemaId"), path);
        }
    }

    /** Platform 回执不合合同（停用条目、行政区国家不符）：按上游无效响应整页失败。 */
    @Test
    void test_platformInvalidResponse() throws Exception {
        RESPONSES.put(SCHEMA_PATH, schema("AE", localAddress(SUBDIVISION), genericAddress(null), null));
        var inactive = country("AE", "United Arab Emirates", null, "971");
        inactive.setStatus(ReferenceDataStatus.INACTIVE.getCode());
        RESPONSES.put(COUNTRIES, countries(inactive));

        assertProblem(get("/client-onboarding/v1/form-schema?jurisdiction=AE&partyType=ORGANIZATION"), 502,
            CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getCode());

        RESPONSES.put(COUNTRIES, countries(country("AE", "United Arab Emirates", null, "971")));
        RESPONSES.put(SUBDIVISIONS, subdivisions(subdivision("SA-01", "SA", "Riyadh")));

        assertProblem(get("/client-onboarding/v1/form-schema?jurisdiction=AE&partyType=ORGANIZATION"), 502,
            CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getCode());
    }

    /** client-master Schema 不可用时不读取 Platform。 */
    @Test
    void test_ownerUnavailable() throws Exception {
        FAILURES.put(SCHEMA_PATH, 503);

        var result = get("/client-onboarding/v1/form-schema?jurisdiction=AE&partyType=ORGANIZATION");

        assertEquals(503, result.statusCode(), result.body());
        assertEquals(List.of(SCHEMA_PATH), CALLS);
    }

    /** 非法查询参数在入站边界拒绝，不到达任何 Owner。 */
    @Test
    void test_invalidParameter() throws Exception {
        assertEquals(400, get("/client-onboarding/v1/form-schema?jurisdiction=ae&partyType=ORGANIZATION").statusCode());
        assertEquals(List.of(), CALLS);
    }

    // ---- 外部 DTO 构造 ----

    private static FormSchemaResponseBody schema(String jurisdiction, FormSchemaResponseBody.AddressFormat local,
        FormSchemaResponseBody.AddressFormat generic, String currencyCatalog) {
        var field = new FormSchemaResponseBody.Field();
        field.setFieldKey("business.expectedVolumeCurrency");
        field.setWidget("SELECT");
        field.setCatalogCode(currencyCatalog);
        field.setRequirementLevel("REQUIRED");
        field.setAppliesTo("PRIMARY");
        field.setLabelKey("onboardingSchema.field.business.expectedVolumeCurrency");
        var model = new FormSchemaResponseBody.Field();
        model.setFieldKey("business.businessModel");
        model.setWidget("SELECT");
        model.setCatalogCode("BUSINESS_MODEL");
        model.setRequirementLevel("REQUIRED");
        model.setAppliesTo("PRIMARY");
        model.setLabelKey("onboardingSchema.field.business.businessModel");
        var group = new FormSchemaResponseBody.Group();
        group.setGroup("PROFILE");
        group.setLabelKey("onboardingSchema.group.PROFILE");
        group.setFields(List.of(field, model));
        var section = new FormSchemaResponseBody.Section();
        section.setSection("BUSINESS");
        section.setLabelKey("onboardingSchema.section.BUSINESS");
        section.setGroups(List.of(group));
        var option = new FormSchemaResponseBody.CatalogOption();
        option.setCode("SERVICES");
        option.setLabelKey("onboardingSchema.catalog.BUSINESS_MODEL.SERVICES");
        var schema = new FormSchemaResponseBody();
        schema.setSchemaId("9270000000700001");
        schema.setVersionNo(1);
        schema.setJurisdictionCode(jurisdiction);
        schema.setPartyType("ORGANIZATION");
        schema.setOrganizationTypeCode("*");
        schema.setSections(List.of(section));
        schema.setCatalogs(Map.of("BUSINESS_MODEL", List.of(option)));
        schema.setOrganizationTypes(List.of());
        schema.setIdentifierTypes(List.of());
        schema.setAddressFormat(local);
        schema.setFallbackAddressFormat(generic);
        schema.setRelationTypes(List.of());
        var rules = new FormSchemaResponseBody.RelationRules();
        rules.setUboThresholdPct("25");
        rules.setUboThresholdComparison("GT");
        schema.setRelationRules(rules);
        schema.setMaterials(List.of());
        var terms = new FormSchemaResponseBody.Declaration();
        terms.setDeclarationType("PLATFORM_TERMS_ACCEPTANCE");
        terms.setLabelKey("onboardingSchema.declaration.PLATFORM_TERMS_ACCEPTANCE");
        terms.setRequirementLevel("REQUIRED");
        terms.setDocumentVersion("2026-10-draft");
        schema.setDeclarations(List.of(terms));
        return schema;
    }

    private static FormSchemaResponseBody.CatalogOption option(String code, String labelKey) {
        var option = new FormSchemaResponseBody.CatalogOption();
        option.setCode(code);
        option.setLabelKey(labelKey);
        return option;
    }

    private static FormSchemaResponseBody.AddressFormat localAddress(String stateCatalog) {
        return address("AE_POBOX", stateCatalog);
    }

    private static FormSchemaResponseBody.AddressFormat genericAddress(String stateCatalog) {
        return address("GENERIC", stateCatalog);
    }

    private static FormSchemaResponseBody.AddressFormat address(String code, String stateCatalog) {
        var state = new FormSchemaResponseBody.AddressComponent();
        state.setComponent("stateOrProvince");
        state.setRequired(true);
        state.setCatalogCode(stateCatalog);
        state.setLabelKey("onboardingSchema.address." + code + ".stateOrProvince");
        state.setOrder(40);
        var country = new FormSchemaResponseBody.AddressComponent();
        country.setComponent("countryCode");
        country.setRequired(true);
        country.setLabelKey("onboardingSchema.address." + code + ".countryCode");
        country.setOrder(60);
        var format = new FormSchemaResponseBody.AddressFormat();
        format.setCode(code);
        format.setComponents(List.of(state, country));
        return format;
    }

    private static ReferenceCountryResponseBody country(String code, String name, String logo, String callingCode) {
        var country = new ReferenceCountryResponseBody();
        country.setCountryCode(code);
        country.setName(name);
        country.setLogoPublicUrl(logo);
        country.setLogoFileAddress(logo == null ? null : "/files/v1/1" + code.charAt(0));
        country.setCallingCode(callingCode);
        country.setStatus(ACTIVE);
        country.setVersion(1);
        return country;
    }

    private static ReferenceCountryListResponseBody countries(ReferenceCountryResponseBody... items) {
        var list = new ReferenceCountryListResponseBody();
        list.setItems(List.of(items));
        return list;
    }

    private static ReferenceCurrencyResponseBody currency(String code, String name, String logo) {
        var currency = new ReferenceCurrencyResponseBody();
        currency.setCurrencyCode(code);
        currency.setName(name);
        currency.setLogoPublicUrl(logo);
        currency.setMinorUnit(2);
        currency.setStatus(ACTIVE);
        currency.setVersion(1);
        return currency;
    }

    private static ReferenceCurrencyListResponseBody currencies(ReferenceCurrencyResponseBody... items) {
        var list = new ReferenceCurrencyListResponseBody();
        list.setItems(List.of(items));
        return list;
    }

    private static CountrySubdivisionResponseBody subdivision(String code, String countryCode, String name) {
        var subdivision = new CountrySubdivisionResponseBody();
        subdivision.setSubdivisionCode(code);
        subdivision.setCountryCode(countryCode);
        subdivision.setName(name);
        subdivision.setStatus(ACTIVE);
        subdivision.setVersion(1);
        return subdivision;
    }

    private static CountrySubdivisionListResponseBody subdivisions(CountrySubdivisionResponseBody... items) {
        var list = new CountrySubdivisionListResponseBody();
        list.setItems(List.of(items));
        return list;
    }

    // ---- 断言与传输 ----

    private static List<String> codes(JsonNode items) {
        var codes = new ArrayList<String>();
        items.forEach(item -> codes.add(item.path("code").asString()));
        return codes;
    }

    private static void assertProblem(HttpResponse<String> result, int status, String code) {
        assertEquals(status, result.statusCode(), result.body());
        assertEquals(code, JSON.readTree(result.body()).path("code").asString(), result.body());
    }

    private HttpResponse<String> get(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("X-Gateway-Realm", "CUSTOMER")
            .header("X-Gateway-Channel", "CLIENT")
            .header("X-Gateway-Actor-Type", "PLATFORM_USER")
            .header("X-Gateway-Context-Type", "ACCOUNT")
            .header("X-Gateway-Actor-Subject", "1")
            .header("X-Gateway-Session-Id", "2")
            .header("Origin", ORIGIN)
            .header("X-Request-Id", "browser-form-schema-fixture")
            .GET()
            .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpServer startOwners() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.createContext("/", ClientFormSchemaHttpAutoTest::respond);
            server.start();
            return server;
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** 只回放已配置的 DTO 或故障；未配置的路径记为意外调用并返回 404。 */
    private static void respond(HttpExchange exchange) throws java.io.IOException {
        String path = exchange.getRequestURI().getPath(), query = exchange.getRequestURI().getQuery();
        CALLS.add(query == null || path.equals(SCHEMA_PATH) ? path : path + "?" + query);
        Integer failure = FAILURES.get(path);
        if (failure != null) {
            write(exchange, failure, "{\"type\":\"about:blank\",\"title\":\"Service Unavailable\",\"detail\":\"Fixture unavailable\","
                + "\"code\":\"SERVICE_UNAVAILABLE\",\"requestId\":\"fixture-owner\",\"timestamp\":\"2026-10-27T00:00:00.000000Z\"}",
                "application/problem+json");
            return;
        }
        Object value = RESPONSES.get(path);
        if (value == null) {
            UNEXPECTED.add(exchange.getRequestMethod() + " " + path);
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        write(exchange, 200, JSON.writeValueAsString(value), "application/json");
    }

    private static void write(HttpExchange exchange, int status, String body, String type) throws java.io.IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("X-Request-Id", "fixture-owner");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
