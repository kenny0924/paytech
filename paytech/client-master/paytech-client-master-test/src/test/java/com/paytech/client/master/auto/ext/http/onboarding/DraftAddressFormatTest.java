package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.client.master.test.base.onboarding.AbstractOnboardingHttpTest;

/**
 * 地址格式码（C04 草稿与人员新建 / 修改）：页面可随地址组件提交 address.addressFormatCode，也可省略
 * <li>提交时须是辖区本地格式或境外通用格式 GENERIC，否则 422 指向该字段；省略时同样保存。</li>
 * <li>落库值始终由服务端按地址国家推导：辖区国家用本地格式，境外地址存 GENERIC。</li>
 *
 * @see com.paytech.client.contract.api.onboarding.ClientDraftApi#save
 * @see com.paytech.client.contract.api.onboarding.ClientPeopleApi
 */
class DraftAddressFormatTest extends AbstractOnboardingHttpTest {

    /** 联系方式经 Vault 保护；提交核对需要程序材料。 */
    @BeforeEach
    void setUpVault() {
        vault.mockProtect();
        owners.mockStandardPrograms();
    }

    /** 沙特国家地址：与页面相同的 PATCH 体（地址组件 + 格式码 + 联系方式）保存成功，三部分都落库。 */
    @Test
    void test_saNationalAddressWithContacts() {
        long applicationId = started("ORGANIZATION", "SA", "LLC", null);

        var result = patch(applicationId, """
            {"dirtyFields":["address.countryCode","address.addressFormatCode","address.buildingNumber","address.addressLine1",
            "address.district","address.city","address.postalCode","address.additionalNumber","contact.email","contact.phone"],
            "values":{"address":{"countryCode":"SA","addressFormatCode":"SA_NATIONAL_ADDRESS","buildingNumber":"8228",
            "addressLine1":"King Fahd Road","district":"Al Olaya","city":"SA_RIYADH","postalCode":"12214","additionalNumber":"2121"},
            "contact":{"email":"finance@example.test","phone":"+966501234567"}}}""");

        assertEquals(200, result.getStatus().value(), new String(result.getResponseBody()));
        var address = mapper.address(mapper.applicationProfile(applicationId));
        assertEquals("SA_NATIONAL_ADDRESS", address.get("address_format_code"));
        assertEquals("8228", address.get("building_number"));
        assertEquals("2121", address.get("additional_number"));
        assertEquals("SA_RIYADH", address.get("city"));
        assertEquals(List.of("EMAIL", "PHONE"), mapper.contactTypes(mapper.applicationProfile(applicationId)));
        assertFalse(checkErrors(applicationId).stream().anyMatch(item -> item.startsWith("address.")), checkErrors(applicationId).toString());
    }

    /** 阿联酋 PO Box 地址：格式码可单独提交；境外国家按页面提交的本地格式码也能保存，落库为 GENERIC。 */
    @Test
    void test_aePoBoxAndForeignAddress() {
        long applicationId = started("INDIVIDUAL", "AE", null, null);

        var local = patch(applicationId, """
            {"dirtyFields":["address.countryCode","address.addressFormatCode","address.addressLine1","address.city",
            "address.stateOrProvince","address.poBox"],
            "values":{"address":{"countryCode":"AE","addressFormatCode":"AE_POBOX","addressLine1":"Office 1204, Marina Plaza",
            "city":"Dubai","stateOrProvince":"AE-DU","poBox":"112233"}}}""");

        assertEquals(200, local.getStatus().value(), new String(local.getResponseBody()));
        long profileId = mapper.applicationProfile(applicationId);
        assertEquals("AE_POBOX", mapper.address(profileId).get("address_format_code"));
        assertEquals("112233", mapper.address(profileId).get("po_box"));

        var foreign = patch(applicationId, """
            {"dirtyFields":["address.countryCode","address.addressFormatCode"],
            "values":{"address":{"countryCode":"GB","addressFormatCode":"AE_POBOX"}}}""");

        assertEquals(200, foreign.getStatus().value(), new String(foreign.getResponseBody()));
        assertEquals("GENERIC", mapper.address(profileId).get("address_format_code"));
        assertEquals("GB", mapper.address(profileId).get("country_code"));
    }

    /** 不属于本辖区的格式码：422 INVALID_DRAFT 指向 address.addressFormatCode，同一 PATCH 的其他字段都不落库。 */
    @Test
    void test_foreignJurisdictionFormatRejected() {
        long applicationId = started("INDIVIDUAL", "AE", null, null);

        var result = patch(applicationId, """
            {"dirtyFields":["address.addressFormatCode","address.city"],
            "values":{"address":{"addressFormatCode":"SA_NATIONAL_ADDRESS","city":"Dubai"}}}""");

        assertEquals(422, result.getStatus().value(), new String(result.getResponseBody()));
        assertEquals("INVALID_DRAFT", body(result).path("code").asString());
        assertEquals("address.addressFormatCode", body(result).path("errors").get(0).path("field").asString());
        assertTrue(vault.calls().isEmpty());
    }

    /**
     * 行政区（REF_COUNTRY_SUBDIVISION）保存草稿只校验 ISO 3166-2 格式：旧下划线目录码 422 指向 address.stateOrProvince；
     * 境外通用格式的省州为自由文本照常保存。保存草稿不产生 Platform 参考数据请求。
     */
    @Test
    void test_subdivisionFormat() {
        long applicationId = started("INDIVIDUAL", "AE", null, null);
        long calls = referenceData.count();

        var legacy = patch(applicationId, """
            {"dirtyFields":["address.countryCode","address.stateOrProvince"],
            "values":{"address":{"countryCode":"AE","stateOrProvince":"AE_DU"}}}""");

        assertEquals(422, legacy.getStatus().value(), new String(legacy.getResponseBody()));
        assertEquals("INVALID_DRAFT", body(legacy).path("code").asString());
        assertEquals("address.stateOrProvince", body(legacy).path("errors").get(0).path("field").asString());

        var foreign = patch(applicationId, """
            {"dirtyFields":["address.countryCode","address.stateOrProvince"],
            "values":{"address":{"countryCode":"GB","stateOrProvince":"Greater London"}}}""");

        assertEquals(200, foreign.getStatus().value(), new String(foreign.getResponseBody()));
        assertEquals("Greater London", mapper.address(mapper.applicationProfile(applicationId)).get("state_or_province"));
        assertEquals(calls, referenceData.count());
    }

    /** 页面省略格式码：沙特草稿地址与同批联系方式照常保存，落库为 SA_NATIONAL_ADDRESS。 */
    @Test
    void test_saDraftWithoutFormatCode() {
        long sa = started("ORGANIZATION", "SA", "LLC", null);

        var saved = patch(sa, """
            {"dirtyFields":["address.countryCode","address.buildingNumber","address.addressLine1","address.district","address.city",
            "address.postalCode","address.additionalNumber","contact.email"],
            "values":{"address":{"countryCode":"SA","buildingNumber":"8228","addressLine1":"King Fahd Road","district":"Al Olaya",
            "city":"SA_RIYADH","postalCode":"12214","additionalNumber":"2121"},"contact":{"email":"finance@example.test"}}}""");

        assertEquals(200, saved.getStatus().value(), new String(saved.getResponseBody()));
        assertEquals("SA_NATIONAL_ADDRESS", mapper.address(mapper.applicationProfile(sa)).get("address_format_code"));
        assertEquals(List.of("EMAIL"), mapper.contactTypes(mapper.applicationProfile(sa)));
    }

    /** 页面省略格式码：阿联酋草稿地址落库为 AE_POBOX。 */
    @Test
    void test_aeDraftWithoutFormatCode() {
        long ae = started("INDIVIDUAL", "AE", null, null);
        var aeSaved = patch(ae, """
            {"dirtyFields":["address.countryCode","address.addressLine1","address.city","address.stateOrProvince"],
            "values":{"address":{"countryCode":"AE","addressLine1":"Marina Plaza","city":"Dubai","stateOrProvince":"AE-DU"}}}""");

        assertEquals(200, aeSaved.getStatus().value(), new String(aeSaved.getResponseBody()));
        assertEquals("AE_POBOX", mapper.address(mapper.applicationProfile(ae)).get("address_format_code"));
    }

    /** 人员新建与修改：带格式码与不带格式码的页面原样体都能保存；境外地址存 GENERIC；错误格式码 422 且不建人员。 */
    @Test
    void test_peopleCreateAndUpdate() {
        long applicationId = started("ORGANIZATION", "SA", "LLC", null);

        var withCode = people(applicationId, """
            {"person":{"dirtyFields":["individual.givenName","individual.familyName","address.countryCode","address.addressFormatCode",
            "address.buildingNumber","address.addressLine1","address.district","address.city","address.postalCode","address.additionalNumber"],
            "values":{"individual":{"givenName":"Sara","familyName":"Alharbi"},"address":{"countryCode":"SA",
            "addressFormatCode":"SA_NATIONAL_ADDRESS","buildingNumber":"1111","addressLine1":"Olaya Street","district":"Al Olaya",
            "city":"SA_RIYADH","postalCode":"12211","additionalNumber":"3333"}}},
            "relation":{"relationType":"UBO","ownershipPercentage":"60","effectiveFrom":"2024-01-01"}}""");
        assertEquals(201, withCode.getStatus().value(), new String(withCode.getResponseBody()));
        long first = Long.parseLong(body(withCode).path("partyId").asString());
        assertEquals("SA_NATIONAL_ADDRESS", mapper.address(mapper.currentProfile(first)).get("address_format_code"));

        var withoutCode = people(applicationId, """
            {"person":{"dirtyFields":["individual.givenName","individual.familyName","address.countryCode","address.addressLine1",
            "address.city"],
            "values":{"individual":{"givenName":"Omar","familyName":"Haddad"},"address":{"countryCode":"SA",
            "addressLine1":"Tahlia Street","city":"SA_JEDDAH"}}},
            "relation":{"relationType":"DIRECTOR","effectiveFrom":"2024-01-01"}}""");
        assertEquals(201, withoutCode.getStatus().value(), new String(withoutCode.getResponseBody()));
        long second = Long.parseLong(body(withoutCode).path("partyId").asString());
        assertEquals("SA_NATIONAL_ADDRESS", mapper.address(mapper.currentProfile(second)).get("address_format_code"));

        var moved = http.patch().uri("/client-onboarding/v1/applications/{id}/people/{p}", applicationId, second).headers(this::client)
            .header("Content-Type", "application/json").body("""
                {"dirtyFields":["address.countryCode","address.addressFormatCode","address.addressLine1"],
                "values":{"address":{"countryCode":"GB","addressFormatCode":"SA_NATIONAL_ADDRESS","addressLine1":"1 Baker Street"}}}"""
                .replace("\n", "")).exchange().expectBody(byte[].class).returnResult();
        assertEquals(200, moved.getStatus().value(), new String(moved.getResponseBody()));
        assertEquals("GENERIC", mapper.address(mapper.currentProfile(second)).get("address_format_code"));

        var updatedNoCode = http.patch().uri("/client-onboarding/v1/applications/{id}/people/{p}", applicationId, first).headers(this::client)
            .header("Content-Type", "application/json").body("""
                {"dirtyFields":["address.district"],"values":{"address":{"district":"Al Malqa"}}}""".replace("\n", ""))
            .exchange().expectBody(byte[].class).returnResult();
        assertEquals(200, updatedNoCode.getStatus().value(), new String(updatedNoCode.getResponseBody()));
        assertEquals("SA_NATIONAL_ADDRESS", mapper.address(mapper.currentProfile(first)).get("address_format_code"));

        var wrong = people(applicationId, """
            {"person":{"dirtyFields":["individual.givenName","address.addressFormatCode"],
            "values":{"individual":{"givenName":"Ali"},"address":{"addressFormatCode":"AE_POBOX"}}},
            "relation":{"relationType":"SIGNATORY","effectiveFrom":"2024-01-01"}}""");
        assertEquals(422, wrong.getStatus().value(), new String(wrong.getResponseBody()));
        assertEquals("address.addressFormatCode", body(wrong).path("errors").get(0).path("field").asString());
        assertEquals(2, draft(applicationId).path("people").size());
    }

    /** 以页面原样的 JSON 体新建人员。 */
    EntityExchangeResult<byte[]> people(long applicationId, String json) {
        return http.post().uri("/client-onboarding/v1/applications/{id}/people", applicationId).headers(this::client)
            .header("Content-Type", "application/json").body(json.replace("\n", "")).exchange().expectBody(byte[].class).returnResult();
    }

    /** 以页面原样的 JSON 体调用 C04。 */
    EntityExchangeResult<byte[]> patch(long applicationId, String json) {
        return http.patch().uri("/client-onboarding/v1/applications/{id}/draft", applicationId).headers(this::client)
            .header("Content-Type", "application/json").body(json.replace("\n", "")).exchange().expectBody(byte[].class).returnResult();
    }
}
