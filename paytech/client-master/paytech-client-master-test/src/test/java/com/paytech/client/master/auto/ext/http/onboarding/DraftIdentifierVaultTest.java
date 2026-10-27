package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.client.master.test.base.onboarding.AbstractOnboardingHttpTest;
import com.paytech.client.master.test.mocker.onboarding.OnboardingFixtures;
import com.paytech.client.master.test.mocker.onboarding.VaultMocker;

/**
 * C04 草稿中的标识与扩展属性（AC-03）：按辖区包规范化与检查号码，VAULT_REQUIRED 类型只存 STABLE Token，公开登记号明文保存；
 * 字段问题一次性 422 且不调用 Vault；Vault 不可用 503 且不落库。
 *
 * @see com.paytech.client.contract.api.onboarding.ClientDraftApi#save
 */
class DraftIdentifierVaultTest extends AbstractOnboardingHttpTest {

    /** 程序材料用于需求清单。 */
    @BeforeEach
    void setUpPrograms() {
        owners.mockStandardPrograms();
    }

    /** 阿联酋身份证：规范化后 Protect（STABLE、LEGAL_IDENTIFIER_V1），库中只有 Token、末 4 位与到期日，C05 不回显号码。 */
    @Test
    void test_vaultRequiredIdentifierStoresTokenOnly() {
        long applicationId = started("INDIVIDUAL", "AE", null, null);
        vault.mockProtect();

        saved(applicationId, Map.of("identifier", Map.of("identifierType", "AE_EMIRATES_ID", "identifierValue", "784-1990-1234567-1",
            "expiryDate", "2030-01-01")));

        var identifier = mapper.identifier(profileId(applicationId));
        assertEquals(VaultMocker.token(OnboardingFixtures.IDENTIFIER_DATA_TYPE, "784199012345671"), identifier.get("identifier_vault_token"));
        assertNull(identifier.get("public_identifier_value"));
        assertEquals("****5671", identifier.get("identifier_masked_value"));
        assertNull(identifier.get("identifier_check_result"));
        var call = vault.calls().getFirst();
        assertEquals("client-master-service", call.caller());
        assertEquals("STABLE", call.tokenMode());
        assertEquals("CLIENT_ONBOARDING_IDENTIFIER", call.purpose());
        var values = draft(applicationId).path("values").path("identifier");
        assertEquals("****5671", values.path("identifierMasked").asString());
        assertEquals("2030-01-01", values.path("expiryDate").asString());
        assertTrue(values.path("identifierValue").isMissingNode());
        assertFalse(draft(applicationId).toString().contains("784199012345671"));

        saved(applicationId, values("identifier", "identifierType", "PASSPORT"));

        var cleared = mapper.identifier(profileId(applicationId));
        assertNull(cleared.get("identifier_vault_token"));
        assertNull(cleared.get("identifier_masked_value"));
        assertEquals(1, vault.calls().size());
    }

    /** 营业执照不要求 Vault：保存规范化的公开登记号，不调用 Vault。 */
    @Test
    void test_publicIdentifierWithoutVault() {
        long applicationId = started("ORGANIZATION", "AE", "LLC", null);

        saved(applicationId, Map.of("identifier", Map.of("identifierType", "AE_TRADE_LICENSE", "identifierValue", "cn-123 4567")));

        var identifier = mapper.identifier(profileId(applicationId));
        assertEquals("CN1234567", identifier.get("public_identifier_value"));
        assertNull(identifier.get("identifier_vault_token"));
        assertEquals("****4567", identifier.get("identifier_masked_value"));
        assertTrue(vault.calls().isEmpty());
    }

    /** 沙特身份证：格式与校验位问题保存为检查结论，需求清单与提交核对按证件组报告。 */
    @Test
    void test_formatAndChecksumRecorded() {
        long applicationId = started("INDIVIDUAL", "SA", null, null);
        vault.mockProtect();

        saved(applicationId, values("identifier", "identifierType", "SA_NATIONAL_ID", "identifierValue", "1000000007"));

        assertEquals("IDENTIFIER_CHECKSUM_FAILED", mapper.identifier(profileId(applicationId)).get("identifier_check_result"));
        assertTrue(checkErrors(applicationId).contains("group.PERSON_ID=IDENTIFIER_CHECKSUM_FAILED"));

        saved(applicationId, values("identifier", "identifierValue", "2000000006"));

        assertEquals("IDENTIFIER_FORMAT_INVALID", mapper.identifier(profileId(applicationId)).get("identifier_check_result"));
        assertTrue(checkErrors(applicationId).contains("group.PERSON_ID=IDENTIFIER_FORMAT_INVALID"));

        saved(applicationId, values("identifier", "identifierValue", "1000000008"));

        assertNull(mapper.identifier(profileId(applicationId)).get("identifier_check_result"));
        assertTrue(checkErrors(applicationId).contains("group.PERSON_ID=FIELD_MISSING"), "身份证须填到期日");

        saved(applicationId, values("identifier", "expiryDate", "2031-03-01"));

        assertFalse(checkErrors(applicationId).stream().anyMatch(item -> item.startsWith("group.PERSON_ID")));
    }

    /** 非法字符、辖区外标识类型与不适用的属性：一次性 422 INVALID_DRAFT，不调用 Vault、不落库。 */
    @Test
    void test_invalidFieldsRejectedWithoutVault() {
        long applicationId = started("INDIVIDUAL", "AE", null, null);

        var illegal = saveDraft(applicationId, Map.of("identifier", Map.of("identifierType", "AE_EMIRATES_ID", "identifierValue", "78#4"),
            "attributes", Map.of("AE_TRN", "100000000000003")));

        assertEquals(422, illegal.getStatus().value());
        String problem = new String(illegal.getResponseBody());
        assertTrue(problem.contains("identifier.identifierValue"), problem);
        assertTrue(problem.contains("attributes.AE_TRN"), problem);
        var foreign = saveDraft(applicationId, values("identifier", "identifierType", "SA_NATIONAL_ID"));
        assertEquals(422, foreign.getStatus().value());
        assertTrue(new String(foreign.getResponseBody()).contains("identifier.identifierType"));
        assertNull(mapper.identifier(profileId(applicationId)).get("identifier_type"));
        assertTrue(vault.calls().isEmpty());
    }

    /** Vault 不可用：503 ONBOARDING_DEPENDENCY_UNAVAILABLE，草稿保持原样。 */
    @Test
    void test_vaultUnavailable() {
        long applicationId = started("INDIVIDUAL", "AE", null, null);
        vault.mockUnavailable();

        var result = saveDraft(applicationId, Map.of("identifier", Map.of("identifierType", "PASSPORT", "issuingCountry", "GB",
            "identifierValue", "P1234567")));

        assertEquals(503, result.getStatus().value(), new String(result.getResponseBody()));
        assertEquals("ONBOARDING_DEPENDENCY_UNAVAILABLE", body(result).path("code").asString());
        var identifier = mapper.identifier(profileId(applicationId));
        assertNull(identifier.get("identifier_type"));
        assertNull(identifier.get("identifier_vault_token"));
    }

    /** 扩展属性按定义转换并保存；需求清单按 Schema 报告缺失与已满足项，未就绪。 */
    @Test
    void test_attributesAndRequirements() {
        long applicationId = started("INDIVIDUAL", "AE", null, null);

        saved(applicationId, Map.of("attributes", Map.of("OCCUPATION", "EMPLOYEE", "TAX_RESIDENCE_COUNTRY", "ae")));

        var attributes = draft(applicationId).path("values").path("attributes");
        assertEquals("EMPLOYEE", attributes.path("OCCUPATION").asString());
        assertEquals("AE", attributes.path("TAX_RESIDENCE_COUNTRY").asString());
        var result = requirements(applicationId);
        assertEquals(200, result.getStatus().value(), new String(result.getResponseBody()));
        var list = body(result);
        assertFalse(list.path("ready").asBoolean());
        assertEquals("DRAFT", list.path("status").asString());
        var givenName = list.path("items").valueStream().filter(item -> "individual.givenName".equals(item.path("requirementKey").asString()))
            .findFirst().orElseThrow();
        assertEquals("CURRENTLY_DUE", givenName.path("status").asString());
        assertEquals("FIELD_MISSING", givenName.path("errorCode").asString());
        assertEquals("PROVIDE_FIELD", givenName.path("remediationCode").asString());
        assertEquals("CLIENT", givenName.path("awaitingActionFrom").asString());
        assertTrue(list.path("items").valueStream().anyMatch(item -> "attributes.OCCUPATION".equals(item.path("requirementKey").asString())
            && "SATISFIED".equals(item.path("status").asString())));
        assertTrue(list.path("items").valueStream().anyMatch(item -> "declaration.PLATFORM_TERMS".equals(item.path("requirementKey").asString())
            && "SIGN_DECLARATION".equals(item.path("remediationCode").asString())));
    }

    /** 申请主主体的资料 ID。 */
    long profileId(long applicationId) {
        return mapper.applicationProfile(applicationId);
    }
}
