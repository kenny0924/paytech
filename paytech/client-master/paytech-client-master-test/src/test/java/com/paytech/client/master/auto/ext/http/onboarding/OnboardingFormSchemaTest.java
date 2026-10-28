package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.client.master.test.base.onboarding.AbstractOnboardingHttpTest;

import tools.jackson.databind.JsonNode;

/**
 * CM-1 可入驻辖区与 CM-2 表单渲染规格：Schema、目录、组织 / 标识 / 关系类型、地址格式、关系门槛、材料槽与声明全部来自国家包数据与 compliance 程序。
 *
 * @see com.paytech.client.master.api.onboarding.ClientOnboardingCatalogApi#jurisdictions(String, String, String)
 * @see com.paytech.client.master.api.onboarding.ClientOnboardingCatalogApi#formSchema(String, String, String, String, String, String, String)
 */
class OnboardingFormSchemaTest extends AbstractOnboardingHttpTest {

    /** 三类程序的材料。 */
    @BeforeEach
    void setUpPrograms() {
        owners.mockStandardPrograms();
    }

    /** 辖区列表：ACTIVE 且准入不是 CLOSED，按代码排序，只给文案键；CLOSED 辖区不出现。 */
    @Test
    void test_jurisdictions() {
        var listed = body(jurisdictions());
        assertEquals(List.of("AE", "EG", "SA"), codes(listed, "jurisdictionCode"));
        assertEquals("onboardingSchema.jurisdiction.AE", listed.get(0).path("labelKey").asString());
        assertEquals("OPEN", listed.get(0).path("acceptancePolicy").asString());

        packs.acceptancePolicy("EG", "CLOSED");

        assertEquals(List.of("AE", "SA"), codes(body(jurisdictions()), "jurisdictionCode"));
    }

    /** AE 组织默认 Schema（v2）：分区与字段键、标识 / 关系类型、本地 PO Box 地址格式与 GENERIC 兜底、LLC 门槛与比较方式、组织与关联人员材料、声明及条款版本。 */
    @Test
    void test_aeOrganizationDefault() {
        var result = formSchema("jurisdiction", "AE", "partyType", "ORGANIZATION", "organizationType", "LLC");

        assertEquals(200, result.getStatus().value(), new String(result.getResponseBody()));
        var schema = body(result);
        assertEquals("9270000000900301", schema.path("schemaId").asString());
        assertEquals("*", schema.path("organizationTypeCode").asString());
        assertEquals(List.of("BASIC", "BUSINESS", "PEOPLE", "MATERIALS", "DECLARATIONS"), codes(schema.path("sections"), "section"));
        Set<String> fields = fieldKeys(schema);
        assertTrue(fields.containsAll(Set.of("organization.legalName", "organization.registrationAuthorityCode",
            "identifier.AE_TRADE_LICENSE", "attributes.AE_TRN", "address", "people", "declaration.OWNERSHIP_COMPLETE")), fields.toString());
        assertEquals("AE_POBOX", schema.path("addressFormat").path("code").asString());
        assertEquals("GENERIC", schema.path("fallbackAddressFormat").path("code").asString());
        var state = component(schema.path("addressFormat"), "stateOrProvince");
        assertTrue(state.path("required").asBoolean());
        assertEquals("REF_COUNTRY_SUBDIVISION", state.path("catalogCode").asString());
        assertTrue(schema.path("catalogs").has("REGISTRATION_AUTHORITY"));
        assertTrue(fieldKeys(schema).contains("business.currency"));
        assertFalse(schema.path("catalogs").propertyNames().stream().anyMatch(code -> code.startsWith("REF_")),
            "Platform 参考数据目录由 client-console-bff 补全，不出现在 catalogs");
        assertFalse(schema.path("catalogs").has("STATE_OR_PROVINCE") || schema.path("catalogs").has("CURRENCY"));
        assertTrue(codes(schema.path("catalogs").path("ORGANIZATION_TYPE"), "code").contains("LLC"));
        assertFalse(codes(schema.path("organizationTypes"), "code").contains("SOLE_ESTABLISHMENT"), "个人经营形态不属于组织申请");
        var license = item(schema.path("identifierTypes"), "AE_TRADE_LICENSE");
        assertEquals("AUTHORITY", license.path("issuerScopeMode").asString());
        assertEquals("MANUAL_ENTRY", license.path("acquireMode").asString());
        assertTrue(codes(schema.path("relationTypes"), "code").containsAll(List.of("UBO", "DIRECTOR", "SIGNATORY")));
        var rules = schema.path("relationRules");
        assertEquals("25", rules.path("uboThresholdPct").asString());
        assertEquals("CONTROL_THEN_SMO", rules.path("uboFallbackRule").asString());
        assertEquals("GTE", rules.path("uboThresholdComparison").asString());
        assertEquals(1, rules.path("minSignatories").asInt());
        assertTrue(rules.path("requiresDirectors").asBoolean());
        assertEquals(List.of("REGISTRATION:PRIMARY", "IDENTITY:ANY_RELATED"), schema.path("materials").valueStream()
            .map(node -> node.path("requirementCode").asString() + ":" + node.path("subjectRole").asString()).toList());
        assertEquals(8, schema.path("declarations").size());
        assertFalse(fields.contains("declaration.PLATFORM_TERMS"), "v2 拆分了平台条款声明");
        assertEquals("2026-10-draft", item(schema.path("declarations"), "declarationType", "PLATFORM_TERMS_ACCEPTANCE")
            .path("documentVersion").asString());
        assertEquals("2026-10-draft", item(schema.path("declarations"), "declarationType", "PRIVACY_NOTICE_ACKNOWLEDGED")
            .path("documentVersion").asString());
        assertTrue(item(schema.path("declarations"), "declarationType", "INFORMATION_TRUE").path("documentVersion").isNull());
        assertTrue(owners.programCalls().stream().allMatch(call -> "AE".equals(call.jurisdiction()) && call.responsiblePartnerId() == null));
    }

    /** AE FZ_LLC 有专用 Schema（含自由区属性）；PJSC 无专用 Schema 时回落 * 并带只采集控制人的规则。 */
    @Test
    void test_aeOrganizationTypeSpecific() {
        var fz = body(formSchema("jurisdiction", "AE", "partyType", "ORGANIZATION", "organizationType", "FZ_LLC"));
        assertEquals("9270000000900302", fz.path("schemaId").asString());
        assertEquals("FZ_LLC", fz.path("organizationTypeCode").asString());
        assertTrue(fieldKeys(fz).contains("attributes.AE_FREE_ZONE_CODE"));

        var pjsc = body(formSchema("jurisdiction", "AE", "partyType", "ORGANIZATION", "organizationType", "PJSC"));
        assertEquals("9270000000900301", pjsc.path("schemaId").asString());
        assertEquals("CONTROL_ONLY", pjsc.path("relationRules").path("uboCollectionMode").asString());
    }

    /** SA 个人：国民身份证 / Iqama 走 LUHN、护照走签发国作用域；国家地址格式；无关系门槛与关联人员材料。 */
    @Test
    void test_saIndividual() {
        var schema = body(formSchema("jurisdiction", "SA", "partyType", "INDIVIDUAL"));

        assertEquals("SA", schema.path("jurisdictionCode").asString());
        assertEquals("LUHN", item(schema.path("identifierTypes"), "SA_NATIONAL_ID").path("checksumRule").asString());
        assertEquals("LUHN", item(schema.path("identifierTypes"), "SA_IQAMA").path("checksumRule").asString());
        assertEquals("COUNTRY", item(schema.path("identifierTypes"), "PASSPORT").path("issuerScopeMode").asString());
        assertEquals("SA_NATIONAL_ADDRESS", schema.path("addressFormat").path("code").asString());
        assertTrue(schema.path("relationRules").isNull() || schema.path("relationRules").isMissingNode());
        assertEquals(List.of("IDENTITY:PRIMARY", "ADDRESS:PRIMARY"), schema.path("materials").valueStream()
            .map(node -> node.path("requirementCode").asString() + ":" + node.path("subjectRole").asString()).toList());
        var group = schema.path("sections").valueStream().flatMap(section -> section.path("groups").valueStream())
            .flatMap(g -> g.path("fields").valueStream()).filter(field -> "identifier.SA_NATIONAL_ID".equals(field.path("fieldKey").asString()))
            .findFirst().orElseThrow();
        assertEquals("PERSON_ID", group.path("requirementGroup").asString());
    }

    /** EG 组织（v2）：统一商业登记号与本地 CR 号同组至少一项，本地 CR 号按登记机关作用域；增值税号与税卡号分开；EG_NID 校验位规则下发给前端。 */
    @Test
    void test_egOrganization() {
        var schema = body(formSchema("jurisdiction", "EG", "partyType", "ORGANIZATION"));

        assertEquals("NONE", item(schema.path("identifierTypes"), "EG_UNIFIED_CR").path("issuerScopeMode").asString());
        assertEquals("AUTHORITY", item(schema.path("identifierTypes"), "EG_LOCAL_CR").path("issuerScopeMode").asString());
        assertFalse(codes(schema.path("identifierTypes"), "code").contains("EG_COMMERCIAL_REGISTER"), "旧登记号只留给 v1 申请");
        assertTrue(fieldKeys(schema).contains("attributes.EG_VAT_REGISTRATION_NUMBER"));
        assertEquals("EG_NID", item(schema.path("identifierTypes"), "EG_NATIONAL_ID").path("checksumRule").asString());
        assertEquals("EG_STANDARD", schema.path("addressFormat").path("code").asString());
        assertTrue(fieldKeys(schema).contains("attributes.EG_TAX_CARD_NUMBER"));
    }

    /** 准入 CLOSED、未知辖区或缺参数：422 JURISDICTION_NOT_ACCEPTED / 参数错误；compliance 不可用 503，不返回半成品。 */
    @Test
    void test_notAccepted() {
        packs.acceptancePolicy("SA", "CLOSED");

        var closed = formSchema("jurisdiction", "SA", "partyType", "INDIVIDUAL");
        assertEquals(422, closed.getStatus().value());
        assertEquals("JURISDICTION_NOT_ACCEPTED", body(closed).path("code").asString());
        assertEquals("JURISDICTION_NOT_ACCEPTED", body(formSchema("jurisdiction", "XX", "partyType", "INDIVIDUAL")).path("code").asString());
        assertEquals("JURISDICTION_NOT_ACCEPTED", body(start("INDIVIDUAL", "SA", null, null)).path("code").asString());

        owners.mockProgramUnavailable("INDIVIDUAL", "CLIENT");
        var unavailable = formSchema("jurisdiction", "AE", "partyType", "INDIVIDUAL");
        assertEquals(503, unavailable.getStatus().value());
        assertEquals("ONBOARDING_DEPENDENCY_UNAVAILABLE", body(unavailable).path("code").asString());
    }

    /** 按申请取规格：返回申请冻结的 Schema；更换组织类型后切到专用 Schema。 */
    @Test
    void test_byApplication() {
        long applicationId = started("ORGANIZATION", "AE", "LLC", null);
        var frozen = body(formSchema("applicationId", Long.toString(applicationId)));
        assertEquals("9270000000900301", frozen.path("schemaId").asString());
        assertEquals("AE", draft(applicationId).path("jurisdictionCode").asString());

        saved(applicationId, values("organization", "organizationType", "FZ_LLC"));

        assertEquals("9270000000900302", body(formSchema("applicationId", Long.toString(applicationId))).path("schemaId").asString());
        assertEquals("FZ_LLC", draft(applicationId).path("organizationTypeCode").asString());
        var unknown = saveDraft(applicationId, values("organization", "organizationType", "SA_LLC_UNKNOWN"));
        assertEquals(422, unknown.getStatus().value());
        assertTrue(new String(unknown.getResponseBody()).contains("organization.organizationType"));
    }

    /** 数组中某字段的取值列表。 */
    static List<String> codes(JsonNode array, String field) {
        return array.valueStream().map(node -> node.path(field).asString()).toList();
    }

    /** 全部字段键。 */
    static Set<String> fieldKeys(JsonNode schema) {
        return schema.path("sections").valueStream().flatMap(section -> section.path("groups").valueStream())
            .flatMap(group -> group.path("fields").valueStream()).map(field -> field.path("fieldKey").asString())
            .collect(Collectors.toSet());
    }

    /** 按 code 取数组元素。 */
    static JsonNode item(JsonNode array, String code) {
        return item(array, "code", code);
    }

    /** 数组中某属性等于给定值的元素；没有则失败。 */
    static JsonNode item(JsonNode array, String property, String value) {
        return array.valueStream().filter(node -> value.equals(node.path(property).asString())).findFirst()
            .orElseThrow(() -> new AssertionError("缺少 " + value));
    }

    /** 按组件名取地址组件。 */
    static JsonNode component(JsonNode format, String name) {
        return format.path("components").valueStream().filter(node -> name.equals(node.path("component").asString())).findFirst()
            .orElseThrow(() -> new AssertionError("缺少地址组件 " + name));
    }
}
