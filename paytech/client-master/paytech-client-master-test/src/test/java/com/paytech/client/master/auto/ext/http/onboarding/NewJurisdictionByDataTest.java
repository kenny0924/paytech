package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.client.master.test.base.onboarding.AbstractOnboardingHttpTest;
import com.paytech.client.master.test.mocker.onboarding.OnboardingOwnerMocker.Material;
import com.paytech.client.master.test.mocker.platform.ReferenceDataMocker.Catalog;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;

/**
 * 只用数据新增国家（AC-11）：测试辖区 ZZ 的辖区、规则开关、组织类型、自有标识类型、自有关系类型（规则角色 MANAGER）、
 * 扩展属性与 Schema 全部来自夹具数据，不改任何生产代码即可完成 开始申请 → 填写 → 人员 → 材料 → 声明 → 提交 → 审核登记 全链路。
 *
 * @see com.paytech.client.contract.api.onboarding.ClientDraftApi#create
 * @see com.paytech.client.contract.api.onboarding.ClientSubmissionApi#submit(String, String, String, String)
 */
class NewJurisdictionByDataTest extends AbstractOnboardingHttpTest {

    /** 装入 ZZ 包；ZZ 组织与关联人员程序各一项必交材料。 */
    @BeforeEach
    void setUpZz() {
        packs.loadZzPack();
        // 测试辖区 ZZ 的国家只存在于本例的 Platform 参考数据配置中（新增国家同样只改数据）。
        referenceData.mock(Catalog.COUNTRY, "ZZ", ReferenceDataStatus.ACTIVE);
        owners.mockProgram("ORGANIZATION", "CLIENT", new Material("REGISTRATION", "REGISTRATION_CERTIFICATE", true));
        owners.mockProgram("INDIVIDUAL", "RELATED_PERSON", new Material("IDENTITY", "IDENTITY_DOCUMENT", true));
        vault.mockProtect();
    }

    /** ZZ 全链路：规则开关（邮编必填、最低年龄 21、须有经理、免采集受益人）全部生效，I01 携带 ZZ 的代码。 */
    @Test
    void test_zzFullChain() {
        assertTrue(body(jurisdictions()).valueStream().anyMatch(node -> "ZZ".equals(node.path("jurisdictionCode").asString())));
        var schema = body(formSchema("jurisdiction", "ZZ", "partyType", "ORGANIZATION", "organizationType", "ZZ_PARTNERSHIP"));
        assertEquals("9399000000700001", schema.path("schemaId").asString());
        assertTrue(schema.path("relationRules").path("requiresManager").asBoolean());
        assertEquals("EXEMPT", schema.path("relationRules").path("uboCollectionMode").asString());
        assertTrue(schema.path("relationTypes").valueStream().anyMatch(node -> "ZZ_MANAGER".equals(node.path("code").asString())
            && "MANAGER".equals(node.path("ruleRole").asString())));
        assertTrue(schema.path("addressFormat").path("components").valueStream().anyMatch(node -> "postalCode".equals(
            node.path("component").asString()) && node.path("required").asBoolean()));

        long applicationId = started("ORGANIZATION", "ZZ", "ZZ_PARTNERSHIP", null);
        saved(applicationId, Map.of("organization", Map.of("legalName", "Zed Partners"),
            "identifier", Map.of("identifierType", "ZZ_REG_NO", "identifierValue", "zz-123456"),
            "attributes", Map.of("ZZ_LICENSE_CLASS", "CLASS_A"),
            "address", Map.of("addressLine1", "1 Data Street", "city", "Zed City", "countryCode", "ZZ"),
            "business", Map.of("businessModelCode", "SERVICES", "businessDescription", "Advisory", "currency", "USD",
                "expectedVolume", "1000", "expectedTicket", "10")));
        var bad = saveDraft(applicationId, values("attributes", "AE_TRN", "100000000000003"));
        assertEquals(422, bad.getStatus().value(), "其他辖区的属性不适用");

        var errors = checkErrors(applicationId);
        assertTrue(errors.contains("address.postalCode=FIELD_MISSING"), errors.toString());
        assertTrue(errors.contains("people=RELATED_PERSON_INCOMPLETE"), errors.toString());
        assertTrue(errors.contains("relation.MANAGER=MANAGER_MISSING"), errors.toString());
        assertFalse(errors.stream().anyMatch(item -> item.startsWith("relation.UBO")), errors.toString());

        saved(applicationId, values("address", "postalCode", "Z1000"));
        long person = addPerson(applicationId, Map.of("individual", Map.of("givenName", "Zara", "familyName", "Zed",
            "birthDate", "2008-01-01"), "identifier", Map.of("identifierType", "PASSPORT", "issuingCountry", "GB",
            "identifierValue", "zp-998877", "expiryDate", "2032-05-01")), Map.of("relationType", "ZZ_MANAGER", "effectiveFrom", "2024-01-01"));
        assertTrue(checkErrors(applicationId).contains("individual.birthDate=DOB_AGE_UNDER_MINIMUM"));
        var adult = http.patch().uri("/client-onboarding/v1/applications/{id}/people/{p}", applicationId, person).headers(this::client)
            .header("Content-Type", "application/json").body(JSON.writeValueAsString(Map.of("dirtyFields", java.util.List.of(
                "individual.birthDate"), "values", values("individual", "birthDate", "1990-01-01")))).exchange()
            .expectBody(byte[].class).returnResult();
        assertEquals(200, adult.getStatus().value(), new String(adult.getResponseBody()));
        attach(applicationId, partyOf(applicationId), "REGISTRATION", "REGISTRATION_CERTIFICATE");
        attach(applicationId, person, "IDENTITY", "IDENTITY_DOCUMENT");
        assertEquals(200, declare(applicationId, "INFORMATION_TRUE").getStatus().value());
        var declared = draft(applicationId).path("declarations");
        assertEquals(1, declared.size());
        assertEquals("INFORMATION_TRUE", declared.get(0).path("declarationType").asString());
        assertEquals("TRUE", declared.get(0).path("declaredValue").asString());
        assertTrue(draft(applicationId).path("responsiblePartnerId").isNull(), "平台自营无责任 PayFac");

        assertEquals(java.util.List.of(), checkErrors(applicationId));
        owners.mockReviewRegistered(applications.nextId());
        var submitted = submit(applicationId);

        assertEquals(202, submitted.getStatus().value(), new String(submitted.getResponseBody()));
        waitUntil(() -> "UNDER_REVIEW".equals(mapper.application(applicationId).get("status")), Duration.ofSeconds(10));
        var request = owners.reviewRequests().getFirst();
        assertEquals("ZZ", request.getJurisdictionCode());
        assertEquals("ZZ_PARTNERSHIP", request.getOrganizationTypeCode());
        assertEquals("ZZ_REG_NO", request.getSubjects().getFirst().getIdentifierType());
        assertEquals("ZZ_MANAGER", request.getRelations().getFirst().getRelationType());
        assertTrue(request.getAttributes().stream().anyMatch(item -> "ZZ_LICENSE_CLASS".equals(item.getAttributeCode())
            && "CLASS_A".equals(item.getAttributeValue())));
        assertEquals("INFORMATION_TRUE", request.getDeclarations().getFirst().getDeclarationType());
        assertTrue(owners.programCalls().stream().allMatch(call -> "ZZ".equals(call.jurisdiction())));
        assertEquals("ZZ123456", mapper.identityKeys(partyOf(applicationId)).getFirst().get("identifier_key_value"));
    }

    /** 申请主主体 Party ID。 */
    long partyOf(long applicationId) {
        return Long.parseLong(draft(applicationId).path("subjectPartyId").asString());
    }
}
