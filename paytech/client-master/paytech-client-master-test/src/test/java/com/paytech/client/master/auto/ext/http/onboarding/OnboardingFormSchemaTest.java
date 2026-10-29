package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.client.contract.api.enums.ClientDraftErrorCode;
import com.paytech.client.master.domain.enums.RequirementLevel;
import com.paytech.client.master.domain.enums.WidgetCode;
import com.paytech.client.master.domain.onboarding.pack.ReferenceCatalogCodes;
import com.paytech.client.master.test.base.onboarding.AbstractOnboardingHttpTest;
import com.paytech.client.master.test.mocker.commercial.CommercialProductCatalogMocker;
import com.paytech.client.master.test.mocker.onboarding.OnboardingFixtures;

import tools.jackson.databind.JsonNode;

/**
 * CM-1 可入驻辖区与 CM-2 表单渲染规格：Schema、目录、组织 / 标识 / 关系类型、地址格式、关系门槛、材料槽与声明全部来自国家包数据与 compliance 程序。
 *
 * @see com.paytech.client.master.api.onboarding.ClientOnboardingCatalogApi#jurisdictions(String, String, String)
 * @see com.paytech.client.master.api.onboarding.ClientOnboardingCatalogApi#formSchema(String, String, String, String, String, String, String)
 */
class OnboardingFormSchemaTest extends AbstractOnboardingHttpTest {
    private static final String REFUND_URL = "https://shop.example.test/refunds";

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

    /** AE 组织默认 Schema（v3）：分区与字段键、标识 / 关系类型、本地 PO Box 地址格式与 GENERIC 兜底、LLC 门槛与比较方式、组织与关联人员材料、声明及条款版本。 */
    @Test
    void test_aeOrganizationDefault() {
        var result = formSchema("jurisdiction", "AE", "partyType", "ORGANIZATION", "organizationType", "LLC");

        assertEquals(200, result.getStatus().value(), new String(result.getResponseBody()));
        var schema = body(result);
        assertEquals("9281200000900301", schema.path("schemaId").asString());
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
        assertFalse(fields.contains("declaration.PLATFORM_TERMS"), "v2 起拆分了平台条款声明");
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
        assertEquals("9281200000900302", fz.path("schemaId").asString());
        assertEquals("FZ_LLC", fz.path("organizationTypeCode").asString());
        assertTrue(fieldKeys(fz).contains("attributes.AE_FREE_ZONE_CODE"));

        var pjsc = body(formSchema("jurisdiction", "AE", "partyType", "ORGANIZATION", "organizationType", "PJSC"));
        assertEquals("9281200000900301", pjsc.path("schemaId").asString());
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

    /** EG 组织（v3）：统一商业登记号与本地 CR 号同组至少一项，本地 CR 号按登记机关作用域；增值税号与税卡号分开；EG_NID 校验位规则下发给前端。 */
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
        assertEquals("9281200000900301", frozen.path("schemaId").asString());
        assertEquals("AE", draft(applicationId).path("jurisdictionCode").asString());

        saved(applicationId, values("organization", "organizationType", "FZ_LLC"));

        assertEquals("9281200000900302", body(formSchema("applicationId", Long.toString(applicationId))).path("schemaId").asString());
        assertEquals("FZ_LLC", draft(applicationId).path("organizationTypeCode").asString());
        var unknown = saveDraft(applicationId, values("organization", "organizationType", "SA_LLC_UNKNOWN"));
        assertEquals(422, unknown.getStatus().value());
        assertTrue(new String(unknown.getResponseBody()).contains("organization.organizationType"));
    }

    /**
     * 业务信息补采四字段（AC-01）：AE 机构、AE 个人、SA 自由职业者与 EG 个人的 v3 BUSINESS 分区都有四字段；退款政策机构 EVENTUALLY、个人 OPTIONAL；
     * 天数仅 PRE_ORDER 可见并带 1—365 规则；支付方式为 MULTI_SELECT，选项为 commercial ACTIVE 支付方式（文案键 paymentMethod.&lt;代码&gt;），
     * 交付方式为本库目录 FULFILLMENT_TYPE
     */
    @Test
    void test_businessInfoFields() {
        var aeOrganization = body(formSchema("jurisdiction", "AE", "partyType", "ORGANIZATION", "organizationType", "LLC"));
        var aeIndividual = body(formSchema("jurisdiction", "AE", "partyType", "INDIVIDUAL"));
        var saFreelancer = body(formSchema("jurisdiction", "SA", "partyType", "INDIVIDUAL", "organizationType", "FREELANCER"));
        var egIndividual = body(formSchema("jurisdiction", "EG", "partyType", "INDIVIDUAL"));

        assertEquals(RequirementLevel.EVENTUALLY.getCode(), field(aeOrganization, OnboardingFixtures.REFUND_POLICY_URL)
            .path("requirementLevel").asString());
        for (JsonNode individual : List.of(aeIndividual, saFreelancer, egIndividual)) {
            assertEquals(RequirementLevel.OPTIONAL.getCode(), field(individual, OnboardingFixtures.REFUND_POLICY_URL)
                .path("requirementLevel").asString(), individual.path("schemaId").asString());
        }
        assertEquals("9281200000900309", saFreelancer.path("schemaId").asString());
        assertEquals(2, saFreelancer.path("versionNo").asInt());
        for (JsonNode schema : List.of(aeOrganization, aeIndividual, saFreelancer, egIndividual)) {
            var methods = field(schema, OnboardingFixtures.EXPECTED_PAYMENT_METHODS);
            assertEquals(WidgetCode.MULTI_SELECT.getCode(), methods.path("widget").asString());
            assertEquals(ReferenceCatalogCodes.COMMERCIAL_PAYMENT_METHOD, methods.path("catalogCode").asString());
            assertEquals(RequirementLevel.REQUIRED.getCode(), methods.path("requirementLevel").asString());
            assertEquals("onboardingSchema.help.business.expectedPaymentMethods", methods.path("helpKey").asString());
            var type = field(schema, OnboardingFixtures.FULFILLMENT_TYPE);
            assertEquals(WidgetCode.SELECT.getCode(), type.path("widget").asString());
            assertEquals(OnboardingFixtures.FULFILLMENT_TYPE_CATALOG, type.path("catalogCode").asString());
            var days = field(schema, OnboardingFixtures.FULFILLMENT_DAYS);
            assertEquals(OnboardingFixtures.FULFILLMENT_TYPE + "=" + OnboardingFixtures.PRE_ORDER, days.path("visibleWhen").asString());
            assertTrue("365".matches(days.path("validation").path("pattern").asString())
                && !"366".matches(days.path("validation").path("pattern").asString())
                && !"0".matches(days.path("validation").path("pattern").asString()), days.toString());
            assertTrue(REFUND_URL.matches(field(schema, OnboardingFixtures.REFUND_POLICY_URL).path("validation").path("pattern").asString()));
            assertFalse("http://shop.example.test/refunds".matches(field(schema, OnboardingFixtures.REFUND_POLICY_URL)
                .path("validation").path("pattern").asString()));
            assertEquals(CommercialProductCatalogMocker.STANDARD_PAYMENT_METHODS,
                codes(schema.path("catalogs").path(ReferenceCatalogCodes.COMMERCIAL_PAYMENT_METHOD), "code"));
            assertEquals("onboardingSchema.paymentMethod.CARD", schema.path("catalogs").path(ReferenceCatalogCodes.COMMERCIAL_PAYMENT_METHOD)
                .get(0).path("labelKey").asString());
            assertEquals(OnboardingFixtures.FULFILLMENT_TYPES, codes(schema.path("catalogs").path(OnboardingFixtures.FULFILLMENT_TYPE_CATALOG),
                "code"));
        }
        assertEquals("FULFILLMENT", schema(aeOrganization, OnboardingFixtures.FULFILLMENT_TYPE).path("group").asString());
        assertEquals(4, productCatalog.calls().size());
        assertTrue(productCatalog.calls().stream().allMatch(call -> call.productCode() == null
            && CommercialProductCatalogMocker.ACTIVE.equals(call.status())), productCatalog.calls().toString());
    }

    /** 支付方式选项只取 PAYMENT_METHOD 且 ACTIVE 的可开通项，按 commercial 返回顺序去重；功能类与已停用的项不出现。 */
    @Test
    void test_paymentMethodCatalogFiltered() {
        productCatalog.mockCapabilities(List.of(
            CommercialProductCatalogMocker.capability("STC_PAY", CommercialProductCatalogMocker.PAYMENT_METHOD, CommercialProductCatalogMocker.ACTIVE),
            CommercialProductCatalogMocker.capability("CHECKOUT_BRANDING", CommercialProductCatalogMocker.FEATURE,
                CommercialProductCatalogMocker.ACTIVE),
            CommercialProductCatalogMocker.capability("GOOGLE_PAY", CommercialProductCatalogMocker.PAYMENT_METHOD,
                CommercialProductCatalogMocker.RETIRED),
            CommercialProductCatalogMocker.capability("CARD", CommercialProductCatalogMocker.PAYMENT_METHOD, CommercialProductCatalogMocker.ACTIVE),
            CommercialProductCatalogMocker.capability("STC_PAY", CommercialProductCatalogMocker.PAYMENT_METHOD, CommercialProductCatalogMocker.ACTIVE)));

        var schema = body(formSchema("jurisdiction", "AE", "partyType", "INDIVIDUAL"));

        assertEquals(List.of("STC_PAY", "CARD"), codes(schema.path("catalogs").path(ReferenceCatalogCodes.COMMERCIAL_PAYMENT_METHOD), "code"));
    }

    /** commercial 返回 503、断连、缺少列表或代码不合规：表单规格整体 503 ONBOARDING_DEPENDENCY_UNAVAILABLE，不返回缺选项的 Schema。 */
    @Test
    void test_paymentMethodCatalogUnavailable() {
        List<Runnable> faults = List.of(productCatalog::mockUnavailable, productCatalog::mockDisconnect, productCatalog::mockMissingList,
            () -> productCatalog.mockCapabilities(List.of(CommercialProductCatalogMocker.capability("apple pay",
                CommercialProductCatalogMocker.PAYMENT_METHOD, CommercialProductCatalogMocker.ACTIVE))));
        for (Runnable fault : faults) {
            fault.run();
            int before = productCatalog.calls().size();

            var result = formSchema("jurisdiction", "AE", "partyType", "ORGANIZATION", "organizationType", "LLC");

            assertEquals(503, result.getStatus().value(), new String(result.getResponseBody()));
            assertEquals(ClientDraftErrorCode.ONBOARDING_DEPENDENCY_UNAVAILABLE.getCode(), body(result).path("code").asString());
            assertTrue(productCatalog.calls().size() > before, "每种故障都应真实调用 commercial");
        }
    }

    /** 某字段的分组节点。 */
    static JsonNode schema(JsonNode schema, String fieldKey) {
        return schema.path("sections").valueStream().flatMap(section -> section.path("groups").valueStream())
            .filter(group -> group.path("fields").valueStream().anyMatch(field -> fieldKey.equals(field.path("fieldKey").asString())))
            .findFirst().orElseThrow(() -> new AssertionError("缺少字段 " + fieldKey));
    }

    /** 按字段键取字段；没有则失败。 */
    static JsonNode field(JsonNode schema, String fieldKey) {
        return schema.path("sections").valueStream().flatMap(section -> section.path("groups").valueStream())
            .flatMap(group -> group.path("fields").valueStream()).filter(field -> fieldKey.equals(field.path("fieldKey").asString()))
            .findFirst().orElseThrow(() -> new AssertionError("缺少字段 " + fieldKey + "，Schema " + schema.path("schemaId").asString()));
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
