package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.client.contract.api.enums.ClientDraftErrorCode;
import com.paytech.client.master.api.enums.OnboardingRequirementCode;
import com.paytech.client.master.api.enums.OnboardingRequirementStatus;
import com.paytech.client.master.domain.enums.ClientApplicationStatus;
import com.paytech.client.master.domain.onboarding.pack.ReferenceCatalogCodes;
import com.paytech.client.master.test.base.onboarding.AbstractOnboardingHttpTest;
import com.paytech.client.master.test.mocker.commercial.CommercialProductCatalogMocker;
import com.paytech.client.master.test.mocker.onboarding.OnboardingApplicationMocker.ApplicationFixture;
import com.paytech.client.master.test.mocker.onboarding.OnboardingFixtures;

import tools.jackson.databind.JsonNode;

/**
 * 业务信息补采（REQ-12）：草稿规范化与校验、交付天数只随预售保留、需求清单与提交的 Schema 校验、commercial 支付方式目录依赖、
 * 在途旧版本申请不受影响。
 *
 * @see com.paytech.client.contract.api.onboarding.ClientDraftApi#save(String, String, String, String,
 *      com.paytech.client.contract.api.onboarding.ClientDraftModels.DraftPatchRequestBody)
 * @see com.paytech.client.contract.api.onboarding.ClientDraftApi#read(String, String, String, String)
 * @see com.paytech.client.master.api.onboarding.ClientOnboardingApplicationApi#requirements(String, String, String, String)
 * @see com.paytech.client.contract.api.onboarding.ClientSubmissionApi#check(String, String, String, String)
 * @see com.paytech.client.contract.api.onboarding.ClientSubmissionApi#submit(String, String, String, String)
 * @see com.paytech.client.master.api.onboarding.ClientOnboardingCatalogApi#formSchema(String, String, String, String, String, String,
 *      String)
 */
class BusinessInfoCollectionTest extends AbstractOnboardingHttpTest {
    private static final String REFUND_URL = "https://shop.example.test/refunds";

    /** 本类公共配置：三类程序的材料。 */
    @BeforeEach
    void setUpClassMocks() {
        owners.mockStandardPrograms();
    }

    /** 草稿规范化：支付方式拆分、去空白、转大写、去重并按代码升序；天数去前导零；四列落库；保存草稿不调用 commercial。 */
    @Test
    void test_draftNormalizesBusinessInfo() {
        long applicationId = started("ORGANIZATION", "AE", "LLC", null);

        saved(applicationId, Map.of("business", business(" card , apple_pay,CARD", REFUND_URL, OnboardingFixtures.PRE_ORDER, "007")));

        var values = draft(applicationId).path("values").path("business");
        assertEquals("APPLE_PAY,CARD", values.path(field(OnboardingFixtures.EXPECTED_PAYMENT_METHODS)).asString());
        assertEquals(REFUND_URL, values.path(field(OnboardingFixtures.REFUND_POLICY_URL)).asString());
        assertEquals(OnboardingFixtures.PRE_ORDER, values.path(field(OnboardingFixtures.FULFILLMENT_TYPE)).asString());
        assertEquals("7", values.path(field(OnboardingFixtures.FULFILLMENT_DAYS)).asString());
        var row = mapper.businessInfo(mapper.applicationClient(applicationId));
        assertEquals("APPLE_PAY,CARD", row.get("expected_payment_methods"));
        assertEquals(7, ((Number)row.get("fulfillment_days")).intValue());
        assertEquals(1L, mapper.draftRevision(applicationId));
        assertTrue(productCatalog.calls().isEmpty(), "草稿只做语法规范化，不读 commercial");
    }

    /** 非法代码、超过 20 项、http 退款政策、非整数天数：422 INVALID_DRAFT 逐字段返回，不落库、不改资料版本。 */
    @Test
    void test_invalidBusinessInfoRejected() {
        long applicationId = started("ORGANIZATION", "AE", "LLC", null);

        var invalid = saveDraft(applicationId, Map.of("business", business("CARD,apple-pay", "http://shop.example.test/refunds",
            OnboardingFixtures.PRE_ORDER, "abc")));
        var tooMany = saveDraft(applicationId, Map.of("business", Map.of(field(OnboardingFixtures.EXPECTED_PAYMENT_METHODS),
            IntStream.rangeClosed(1, 21).mapToObj(i -> "M" + i).collect(Collectors.joining(",")))));

        assertEquals(422, invalid.getStatus().value(), new String(invalid.getResponseBody()));
        assertEquals(ClientDraftErrorCode.INVALID_DRAFT.getCode(), body(invalid).path("code").asString());
        assertEquals(Map.of(OnboardingFixtures.EXPECTED_PAYMENT_METHODS, "须为以逗号分隔的代码，最多 20 项",
            OnboardingFixtures.REFUND_POLICY_URL, "须为 https 网址", OnboardingFixtures.FULFILLMENT_DAYS, "须为整数"), errors(invalid));
        assertFalse(new String(invalid.getResponseBody()).contains("apple-pay"), "错误不回显提交值");
        assertEquals(Map.of(OnboardingFixtures.EXPECTED_PAYMENT_METHODS, "须为以逗号分隔的代码，最多 20 项"), errors(tooMany));
        var row = mapper.businessInfo(mapper.applicationClient(applicationId));
        assertNull(row.get("expected_payment_methods"));
        assertNull(row.get("fulfillment_type"));
        assertEquals(0L, mapper.draftRevision(applicationId));
    }

    /** 预计交付天数只随预售保留：改为即时交付同一写入清空天数；非预售单独填写天数 422；全为空项的支付方式视为清空。 */
    @Test
    void test_fulfillmentDaysFollowPreOrder() {
        long applicationId = started("ORGANIZATION", "AE", "LLC", null);
        long clientId = mapper.applicationClient(applicationId);
        saved(applicationId, Map.of("business", business("CARD", null, OnboardingFixtures.PRE_ORDER, "30")));

        saved(applicationId, values("business", field(OnboardingFixtures.FULFILLMENT_TYPE), OnboardingFixtures.INSTANT,
            field(OnboardingFixtures.EXPECTED_PAYMENT_METHODS), " , "));

        var row = mapper.businessInfo(clientId);
        assertEquals(OnboardingFixtures.INSTANT, row.get("fulfillment_type"));
        assertNull(row.get("fulfillment_days"), "改为非预售时同一写入清空天数");
        assertNull(row.get("expected_payment_methods"));
        assertTrue(draft(applicationId).path("values").path("business").path(field(OnboardingFixtures.FULFILLMENT_DAYS)).isNull());
        var orphan = saveDraft(applicationId, values("business", field(OnboardingFixtures.FULFILLMENT_DAYS), "5"));
        assertEquals(422, orphan.getStatus().value(), new String(orphan.getResponseBody()));
        assertEquals(Map.of(OnboardingFixtures.FULFILLMENT_DAYS, "仅在交付方式为预售时填写"), errors(orphan));
        var mixed = saveDraft(applicationId, values("business", field(OnboardingFixtures.FULFILLMENT_TYPE), OnboardingFixtures.INSTANT,
            field(OnboardingFixtures.FULFILLMENT_DAYS), "5"));
        assertEquals(422, mixed.getStatus().value(), new String(mixed.getResponseBody()));
        saved(applicationId, values("business", field(OnboardingFixtures.FULFILLMENT_TYPE), OnboardingFixtures.PRE_ORDER,
            field(OnboardingFixtures.FULFILLMENT_DAYS), "5"));
        assertEquals(5, ((Number)mapper.businessInfo(clientId).get("fulfillment_days")).intValue());
    }

    /**
     * 机构需求清单：未选支付方式与交付方式 CURRENTLY_DUE、退款政策 EVENTUALLY_DUE、天数不可见；预售缺天数 CURRENTLY_DUE、366 天格式不符；
     * 含已停用的支付方式不在目录。commercial 只按 status=ACTIVE、不限产品查询
     */
    @Test
    void test_organizationRequirements() {
        long applicationId = started("ORGANIZATION", "AE", "LLC", null);

        var empty = requirementItems(applicationId);
        assertItem(empty, OnboardingFixtures.EXPECTED_PAYMENT_METHODS, OnboardingRequirementStatus.CURRENTLY_DUE,
            OnboardingRequirementCode.FIELD_MISSING);
        assertItem(empty, OnboardingFixtures.FULFILLMENT_TYPE, OnboardingRequirementStatus.CURRENTLY_DUE, OnboardingRequirementCode.FIELD_MISSING);
        assertItem(empty, OnboardingFixtures.REFUND_POLICY_URL, OnboardingRequirementStatus.EVENTUALLY_DUE,
            OnboardingRequirementCode.FIELD_MISSING);
        assertFalse(empty.containsKey(OnboardingFixtures.FULFILLMENT_DAYS), "非预售时天数不可见、不校验");
        assertEquals(List.of(new CommercialProductCatalogMocker.Call(null, CommercialProductCatalogMocker.ACTIVE)), productCatalog.calls());

        saved(applicationId, Map.of("business", business("CARD,GOOGLE_PAY", REFUND_URL, OnboardingFixtures.PRE_ORDER, null)));
        productCatalog.mockCapabilities(List.of(
            CommercialProductCatalogMocker.capability("CARD", CommercialProductCatalogMocker.PAYMENT_METHOD, CommercialProductCatalogMocker.ACTIVE),
            CommercialProductCatalogMocker.capability("GOOGLE_PAY", CommercialProductCatalogMocker.PAYMENT_METHOD,
                CommercialProductCatalogMocker.RETIRED)));

        var preOrder = requirementItems(applicationId);
        assertItem(preOrder, OnboardingFixtures.EXPECTED_PAYMENT_METHODS, OnboardingRequirementStatus.CURRENTLY_DUE,
            OnboardingRequirementCode.FIELD_VALUE_NOT_IN_CATALOG);
        assertItem(preOrder, OnboardingFixtures.FULFILLMENT_DAYS, OnboardingRequirementStatus.CURRENTLY_DUE,
            OnboardingRequirementCode.FIELD_MISSING);
        assertItem(preOrder, OnboardingFixtures.FULFILLMENT_TYPE, OnboardingRequirementStatus.SATISFIED, null);
        assertItem(preOrder, OnboardingFixtures.REFUND_POLICY_URL, OnboardingRequirementStatus.SATISFIED, null);

        saved(applicationId, values("business", field(OnboardingFixtures.FULFILLMENT_DAYS), "366",
            field(OnboardingFixtures.EXPECTED_PAYMENT_METHODS), "CARD"));

        var outOfRange = requirementItems(applicationId);
        assertItem(outOfRange, OnboardingFixtures.FULFILLMENT_DAYS, OnboardingRequirementStatus.CURRENTLY_DUE,
            OnboardingRequirementCode.FIELD_FORMAT_INVALID);
        assertItem(outOfRange, OnboardingFixtures.EXPECTED_PAYMENT_METHODS, OnboardingRequirementStatus.SATISFIED, null);
    }

    /** 个人需求清单：退款政策 OPTIONAL 缺失时不形成清单项；支付方式与交付方式仍为 CURRENTLY_DUE。 */
    @Test
    void test_individualRefundPolicyOptional() {
        long applicationId = started("INDIVIDUAL", "AE", null, null);

        var items = requirementItems(applicationId);

        assertFalse(items.containsKey(OnboardingFixtures.REFUND_POLICY_URL), items.keySet().toString());
        assertItem(items, OnboardingFixtures.EXPECTED_PAYMENT_METHODS, OnboardingRequirementStatus.CURRENTLY_DUE,
            OnboardingRequirementCode.FIELD_MISSING);
        assertItem(items, OnboardingFixtures.FULFILLMENT_TYPE, OnboardingRequirementStatus.CURRENTLY_DUE, OnboardingRequirementCode.FIELD_MISSING);
    }

    /** 机构 v3 申请缺退款政策：清单为 EVENTUALLY_DUE、提交核对无错误，提交 202 进入审核（EVENTUALLY 不阻断，S-04）。 */
    @Test
    void test_organizationSubmitsWithoutRefundPolicy() {
        var app = applications.organization(ACCOUNT_ID);
        var person = applications.staffedPerson(app);
        attachFixture(app, app.partyId(), "REGISTRATION", "REGISTRATION_CERTIFICATE");
        attachFixture(app, person.partyId(), "IDENTITY", "IDENTITY_DOCUMENT");
        mapper.freezeSchema(app.applicationId(), OnboardingFixtures.AE_ORGANIZATION_SCHEMA_V3);
        mapper.businessInfo(app.clientId(), "APPLE_PAY,CARD", null, OnboardingFixtures.INSTANT, null);
        declareAll(app, OnboardingFixtures.ORGANIZATION_DECLARATIONS_V2);

        assertItem(requirementItems(app.applicationId()), OnboardingFixtures.REFUND_POLICY_URL, OnboardingRequirementStatus.EVENTUALLY_DUE,
            OnboardingRequirementCode.FIELD_MISSING);
        assertEquals(List.of(), checkErrors(app.applicationId()));
        owners.mockReviewRegistered(applications.nextId());
        var submitted = submit(app.applicationId());

        assertEquals(202, submitted.getStatus().value(), new String(submitted.getResponseBody()));
        waitUntil(() -> ClientApplicationStatus.UNDER_REVIEW.getCode().equals(mapper.application(app.applicationId()).get("status")),
            Duration.ofSeconds(10));
        assertEquals("APPLE_PAY,CARD", mapper.businessInfo(app.clientId()).get("expected_payment_methods"));
    }

    /** commercial 不可用：需求清单与提交 503 ONBOARDING_DEPENDENCY_UNAVAILABLE，申请保持草稿、资料版本不变、不登记审核。 */
    @Test
    void test_commercialUnavailableBlocksSubmission() {
        var app = completeIndividualV3();
        long revision = mapper.draftRevision(app.applicationId());
        productCatalog.mockUnavailable();

        var requirements = requirements(app.applicationId());
        var submitted = submit(app.applicationId());

        assertEquals(503, requirements.getStatus().value(), new String(requirements.getResponseBody()));
        assertEquals(ClientDraftErrorCode.ONBOARDING_DEPENDENCY_UNAVAILABLE.getCode(), body(requirements).path("code").asString());
        assertEquals(503, submitted.getStatus().value(), new String(submitted.getResponseBody()));
        assertEquals(ClientDraftErrorCode.ONBOARDING_DEPENDENCY_UNAVAILABLE.getCode(), body(submitted).path("code").asString());
        assertEquals(ClientApplicationStatus.DRAFT.getCode(), mapper.application(app.applicationId()).get("status"));
        assertEquals(revision, mapper.draftRevision(app.applicationId()));
        assertTrue(owners.reviewRequests().isEmpty());

        productCatalog.mockStandardCatalog();
        owners.mockReviewRegistered(applications.nextId());
        var retried = submit(app.applicationId());
        assertEquals(202, retried.getStatus().value(), new String(retried.getResponseBody()));
        waitUntil(() -> ClientApplicationStatus.UNDER_REVIEW.getCode().equals(mapper.application(app.applicationId()).get("status")),
            Duration.ofSeconds(10));
    }

    /** 在途 v2 申请（B4 / AC-04）：表单与清单不出现新字段、不调用 commercial，可照常提交。 */
    @Test
    void test_previousVersionApplicationUnaffected() {
        var app = applications.individual(ACCOUNT_ID);
        attachFixture(app, app.partyId(), "IDENTITY", "IDENTITY_DOCUMENT");
        mapper.freezeSchema(app.applicationId(), OnboardingFixtures.AE_INDIVIDUAL_SCHEMA_V2);
        declareAll(app, OnboardingFixtures.INDIVIDUAL_DECLARATIONS_V2);

        var schema = body(formSchema("applicationId", Long.toString(app.applicationId())));
        assertEquals(Long.toString(OnboardingFixtures.AE_INDIVIDUAL_SCHEMA_V2), schema.path("schemaId").asString());
        assertFalse(OnboardingFormSchemaTest.fieldKeys(schema).contains(OnboardingFixtures.EXPECTED_PAYMENT_METHODS));
        assertFalse(schema.path("catalogs").has(ReferenceCatalogCodes.COMMERCIAL_PAYMENT_METHOD));
        assertFalse(requirementItems(app.applicationId()).keySet().stream().anyMatch(key -> key.startsWith("business.")
            && List.of(OnboardingFixtures.EXPECTED_PAYMENT_METHODS, OnboardingFixtures.REFUND_POLICY_URL, OnboardingFixtures.FULFILLMENT_TYPE,
                OnboardingFixtures.FULFILLMENT_DAYS).contains(key)));
        assertEquals(List.of(), checkErrors(app.applicationId()));
        owners.mockReviewRegistered(applications.nextId());

        var submitted = submit(app.applicationId());

        assertEquals(202, submitted.getStatus().value(), new String(submitted.getResponseBody()));
        waitUntil(() -> ClientApplicationStatus.UNDER_REVIEW.getCode().equals(mapper.application(app.applicationId()).get("status")),
            Duration.ofSeconds(10));
        assertTrue(productCatalog.calls().isEmpty(), "旧版本 Schema 不引用 commercial 目录");
    }

    /** 个人夹具改冻结 v3 并补齐业务信息与 v2 声明：可直接提交。 */
    ApplicationFixture completeIndividualV3() {
        var app = applications.individual(ACCOUNT_ID);
        attachFixture(app, app.partyId(), "IDENTITY", "IDENTITY_DOCUMENT");
        mapper.freezeSchema(app.applicationId(), OnboardingFixtures.AE_INDIVIDUAL_SCHEMA_V3);
        mapper.businessInfo(app.clientId(), "CARD", null, OnboardingFixtures.PRE_ORDER, 14);
        declareAll(app, OnboardingFixtures.INDIVIDUAL_DECLARATIONS_V2);
        return app;
    }

    /** 以当前条款版本签署全部声明（声明人联系方式由 Vault Mock 还原）。 */
    void declareAll(ApplicationFixture app, List<String> types) {
        mapper.contactTokens(app.profileId()).forEach((type, token) -> vault.mockReveal(token, "EMAIL".equals(type)
            ? "declarant@example.test" : "+971500000001"));
        for (String type : types) {
            var declared = declare(app.applicationId(), type, OnboardingFixtures.termsVersion(type));
            assertEquals(200, declared.getStatus().value(), new String(declared.getResponseBody()));
        }
    }

    /** 以夹具方式插入一份可用材料，不经过写入口。 */
    void attachFixture(ApplicationFixture app, long subjectPartyId, String requirementCode, String documentType) {
        long fileId = applications.nextId();
        owners.mockFile(ACCOUNT_ID, fileId, HASH, "AVAILABLE");
        applications.document(app, subjectPartyId, requirementCode, documentType, fileId, HASH);
    }

    /** 需求清单项：需求键到项。 */
    Map<String, JsonNode> requirementItems(long applicationId) {
        var result = requirements(applicationId);
        assertEquals(200, result.getStatus().value(), new String(result.getResponseBody()));
        Map<String, JsonNode> items = new LinkedHashMap<>();
        body(result).path("items").forEach(item -> items.put(item.path("requirementKey").asString(), item));
        return items;
    }

    /** 某需求项的状态与错误码；errorCode 为 null 表示无错误。 */
    static void assertItem(Map<String, JsonNode> items, String key, OnboardingRequirementStatus status, OnboardingRequirementCode code) {
        JsonNode item = items.get(key);
        assertNotNull(item, "缺少需求项 " + key + "，实际 " + items.keySet());
        assertEquals(status.getCode(), item.path("status").asString(), item.toString());
        if (code == null) {
            assertTrue(item.path("errorCode").isNull() || item.path("errorCode").isMissingNode(), item.toString());
        } else {
            assertEquals(code.getCode(), item.path("errorCode").asString(), item.toString());
        }
    }

    /** 业务分区的四个新字段；null 取值不出现在请求中。 */
    static Map<String, String> business(String paymentMethods, String refundPolicyUrl, String fulfillmentType, String fulfillmentDays) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(field(OnboardingFixtures.EXPECTED_PAYMENT_METHODS), paymentMethods);
        fields.put(field(OnboardingFixtures.REFUND_POLICY_URL), refundPolicyUrl);
        fields.put(field(OnboardingFixtures.FULFILLMENT_TYPE), fulfillmentType);
        fields.put(field(OnboardingFixtures.FULFILLMENT_DAYS), fulfillmentDays);
        fields.values().removeIf(java.util.Objects::isNull);
        return fields;
    }

    /** 字段键去掉分区前缀。 */
    static String field(String fieldKey) {
        return fieldKey.substring(fieldKey.indexOf('.') + 1);
    }

    /** INVALID_DRAFT 的字段错误：路径到消息。 */
    static Map<String, String> errors(org.springframework.test.web.servlet.client.EntityExchangeResult<byte[]> result) {
        assertEquals(422, result.getStatus().value(), new String(result.getResponseBody()));
        Map<String, String> errors = new LinkedHashMap<>();
        body(result).path("errors").forEach(error -> errors.put(error.path("field").asString(), error.path("message").asString()));
        return errors;
    }
}
