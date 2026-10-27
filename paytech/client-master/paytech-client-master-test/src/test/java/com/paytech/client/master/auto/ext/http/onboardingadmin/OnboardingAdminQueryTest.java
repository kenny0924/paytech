package com.paytech.client.master.auto.ext.http.onboardingadmin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.client.master.test.base.ClientMasterMockTestBase;
import com.paytech.client.master.test.mocker.onboarding.OnboardingFixtures;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Operations 只读查询：辖区包（规则开关、组织 / 标识 / 关系类型）、采集 Schema 版本、通用目录与 Client 列表 / 详情。
 *
 * @see com.paytech.client.master.api.onboardingadmin.ClientOnboardingAdminApi
 */
class OnboardingAdminQueryTest extends ClientMasterMockTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final long OPERATOR_ID = 9_300_000_009L;

    /** 辖区包：按代码排序，含规则开关与 AE 自有及全局标识类型。 */
    @Test
    void test_jurisdictionPacks() {
        var packs = body(get("/client-master-admin/v1/jurisdictions"));

        assertEquals(List.of("AE", "EG", "SA"), packs.valueStream().map(node -> node.path("jurisdictionCode").asString()).toList());
        var ae = packs.get(0);
        assertEquals("AE_POBOX", ae.path("addressFormatCode").asString());
        assertTrue(ae.path("rules").valueStream().anyMatch(rule -> "UBO_THRESHOLD_PCT".equals(rule.path("ruleCode").asString())
            && "25".equals(rule.path("ruleValue").asString())));
        assertTrue(ae.path("organizationTypes").valueStream().anyMatch(type -> "FZ_LLC".equals(type.path("typeCode").asString())));
        var identifiers = ae.path("identifierTypes").valueStream().map(type -> type.path("identifierTypeCode").asString()).toList();
        assertTrue(identifiers.containsAll(List.of("AE_EMIRATES_ID", "AE_TRADE_LICENSE", "PASSPORT")), identifiers.toString());
        assertTrue(ae.path("relationTypes").valueStream().anyMatch(type -> "UBO".equals(type.path("relationTypeCode").asString())
            && type.path("uboFlag").asBoolean()));
        // 本地地址格式组件按展示顺序；省州引用 Platform 行政区。
        var components = ae.path("addressComponents");
        assertEquals(List.of("addressLine1", "addressLine2", "city", "stateOrProvince", "poBox", "countryCode"),
            components.valueStream().map(component -> component.path("component").asString()).toList());
        var state = components.get(3);
        assertEquals("REF_COUNTRY_SUBDIVISION", state.path("catalogCode").asString());
        assertTrue(state.path("required").asBoolean());
        assertEquals(40, state.path("order").asInt());
        assertTrue(components.get(0).path("catalogCode").isNull());
        assertEquals("REF_COUNTRY_SUBDIVISION", packs.get(1).path("addressComponents").valueStream()
            .filter(component -> "stateOrProvince".equals(component.path("component").asString())).findFirst().orElseThrow()
            .path("catalogCode").asString());
    }

    /** Schema 版本与字段、目录项。 */
    @Test
    void test_schemasAndCatalogs() {
        var schemas = body(get("/client-master-admin/v1/onboarding-schemas?jurisdiction=AE"));

        assertEquals(4, schemas.size());
        var defaultOrganization = schemas.valueStream().filter(node -> "9270000000700001".equals(node.path("schemaId").asString()))
            .findFirst().orElseThrow();
        assertEquals("ACTIVE", defaultOrganization.path("status").asString());
        assertTrue(defaultOrganization.path("fields").valueStream().anyMatch(field -> "identifier.AE_TRADE_LICENSE".equals(
            field.path("fieldKey").asString()) && "ORGANIZATION_ID".equals(field.path("requirementGroup").asString())));
        var authorities = body(get("/client-master-admin/v1/catalog-items?catalogCode=REGISTRATION_AUTHORITY"));
        assertTrue(authorities.valueStream().anyMatch(item -> "AE_DED_DUBAI".equals(item.path("itemCode").asString())
            && "AE".equals(item.path("jurisdictionCode").asString())));
        assertTrue(authorities.valueStream().anyMatch(item -> "SA_MC".equals(item.path("itemCode").asString())
            && "SA".equals(item.path("jurisdictionCode").asString())));
        // 行业、币种与一级行政区已迁至 Platform 参考数据，本库目录不再提供。
        assertEquals(0, body(get("/client-master-admin/v1/catalog-items?catalogCode=INDUSTRY")).size());
        assertEquals(0, body(get("/client-master-admin/v1/catalog-items?catalogCode=STATE_OR_PROVINCE")).size());
    }

    /** Client 列表按辖区与状态过滤；详情给出主档、资料（只有脱敏证件）、人员、关系与申请历史；不存在 404。 */
    @Test
    void test_clients() {
        var app = applications.organization(ACCOUNT_ID);
        var person = applications.staffedPerson(app);
        applications.individual(ACCOUNT_ID + 1);

        var page = body(get("/client-master-admin/v1/clients?jurisdiction=AE&status=PENDING&size=10"));
        assertEquals(2, page.path("total").asInt(), page.toString());
        var summary = page.path("items").valueStream().filter(item -> Long.toString(app.clientId()).equals(item.path("clientId")
            .asString())).findFirst().orElseThrow();
        assertEquals("ORGANIZATION", summary.path("clientType").asString());
        assertEquals("Fixture Trading LLC", summary.path("legalName").asString());
        assertEquals("DRAFT", summary.path("latestApplicationStatus").asString());
        assertEquals(0, body(get("/client-master-admin/v1/clients?jurisdiction=SA")).path("total").asInt());

        var detail = body(get("/client-master-admin/v1/clients/" + app.clientId()));
        assertEquals(OnboardingFixtures.JURISDICTION, detail.path("client").path("jurisdictionCode").asString());
        assertEquals(OnboardingFixtures.masked(OnboardingFixtures.tradeLicense(app.partyId())),
            detail.path("values").path("identifier").path("identifierMasked").asString());
        assertEquals(1, detail.path("people").size());
        assertEquals(Long.toString(person.partyId()), detail.path("people").get(0).path("partyId").asString());
        assertFalse(detail.toString().contains(OnboardingFixtures.passport(person.partyId())), "不得出现证件号原文");
        assertEquals(3, detail.path("relations").size());
        assertEquals(Long.toString(app.applicationId()), detail.path("applications").get(0).path("applicationId").asString());
        assertEquals(404, get("/client-master-admin/v1/clients/" + applications.nextId()).getStatus().value());
    }

    /** 遗留 Client（发布回填后：辖区 LEGACY、无 Schema、证件已脱敏）的列表与详情照常渲染，不出现原文。 */
    @Test
    void test_legacyClientReadable() {
        var app = applications.approved(ACCOUNT_ID, false);
        mapper.legacyShape(app.applicationId(), app.clientId(), app.partyId(), app.profileId());

        var page = body(get("/client-master-admin/v1/clients?status=ACTIVE"));
        assertEquals(1, page.path("total").asInt(), page.toString());
        assertTrue(page.path("items").get(0).path("jurisdictionCode").isNull());
        var detail = body(get("/client-master-admin/v1/clients/" + app.clientId()));
        assertEquals("****1234", detail.path("values").path("identifier").path("identifierMasked").asString());
        var history = detail.path("applications").get(0);
        assertEquals("LEGACY", history.path("jurisdictionCode").asString());
        assertTrue(history.path("schemaId").isNull());
        assertFalse(detail.toString().contains(OnboardingFixtures.passport(app.partyId())), "不得出现证件号原文");
    }

    /** 以 Operations 身份 GET。 */
    EntityExchangeResult<byte[]> get(String uri) {
        return http.get().uri(uri).headers(this::operator).exchange().expectBody(byte[].class).returnResult();
    }

    /** 响应 JSON；非 2xx 时附带正文。 */
    static JsonNode body(EntityExchangeResult<byte[]> result) {
        assertTrue(result.getStatus().is2xxSuccessful(), new String(result.getResponseBody()));
        return JSON.readTree(result.getResponseBody());
    }

    /** Gateway 可信 Operations 头。 */
    void operator(HttpHeaders headers) {
        headers.set("X-Gateway-Actor-Subject", Long.toString(OPERATOR_ID));
        headers.set("X-Gateway-Session-Id", "ops-session-1");
        headers.set("X-Request-Id", "cct-admin-query-test");
    }
}
