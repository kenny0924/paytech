package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.client.master.test.base.onboarding.AbstractOnboardingHttpTest;

/**
 * 人员与关系（新建人员、追加角色、修改角色）使用页面原样请求体：关系不带 effectiveFrom 时取当日（UTC）生效；
 * 不持股的角色 ownershipPercentage 为 null；任何问题都返回带 relation.<字段> 路径的 errors[]。
 *
 * @see com.paytech.client.contract.api.onboarding.ClientPeopleApi
 */
class PeopleWebBodyTest extends AbstractOnboardingHttpTest {

    /** 人员护照经 Vault 保护。 */
    @BeforeEach
    void setUpVault() {
        vault.mockProtect();
    }

    /** AE 受益人 55%、英国地址董事、通过 /relations 追加签字人、修改持股：全部成功，生效日期为当日。 */
    @Test
    void test_webBodies() {
        long applicationId = started("ORGANIZATION", "AE", "LLC", null);

        var ubo = people(applicationId, """
            {"person":{"dirtyFields":["individual.givenName","individual.familyName","individual.birthDate","individual.birthPlace",
            "individual.birthCountry","individual.gender","individual.nationalityCountry","individual.residenceCountry",
            "identifier.identifierType","identifier.issuingCountry","identifier.identifierValue","identifier.expiryDate",
            "address.countryCode","address.addressLine1","address.city","address.stateOrProvince"],
            "values":{"individual":{"givenName":"Mariam","familyName":"Al Mansoori","birthDate":"1985-03-14","birthPlace":"Dubai",
            "birthCountry":"AE","gender":"FEMALE","nationalityCountry":"AE","residenceCountry":"AE"},
            "identifier":{"identifierType":"PASSPORT","issuingCountry":"AE","identifierValue":"N1234567","expiryDate":"2031-05-01"},
            "address":{"countryCode":"AE","addressLine1":"Villa 12, Jumeirah 1","city":"Dubai","stateOrProvince":"AE-DU"}}},
            "relation":{"relationType":"UBO","ownershipPercentage":"55","uboBasis":"OWNERSHIP"}}""");

        assertEquals(201, ubo.getStatus().value(), new String(ubo.getResponseBody()));
        long uboParty = Long.parseLong(body(ubo).path("partyId").asString());
        var uboRelation = mapper.relationsOf(uboParty).getFirst();
        assertEquals("UBO", uboRelation.get("relation_type"));
        assertEquals(0, new java.math.BigDecimal("55").compareTo((java.math.BigDecimal)uboRelation.get("ownership_percentage")));
        assertEquals("OWNERSHIP", uboRelation.get("ubo_basis"));
        assertEquals(LocalDate.now(ZoneOffset.UTC), ((LocalDateTime)uboRelation.get("effective_from")).toLocalDate());

        var director = people(applicationId, """
            {"person":{"dirtyFields":["individual.givenName","individual.familyName","address.countryCode","address.addressLine1",
            "address.city","address.postalCode"],
            "values":{"individual":{"givenName":"James","familyName":"Whitfield"},"address":{"countryCode":"GB",
            "addressLine1":"10 Downing Street","city":"London","postalCode":"SW1A 2AA"}}},
            "relation":{"relationType":"DIRECTOR","ownershipPercentage":null}}""");

        assertEquals(201, director.getStatus().value(), new String(director.getResponseBody()));
        long directorParty = Long.parseLong(body(director).path("partyId").asString());
        assertEquals("GENERIC", mapper.address(mapper.currentProfile(directorParty)).get("address_format_code"));

        var signatory = http.post().uri("/client-onboarding/v1/applications/{id}/relations", applicationId).headers(this::client)
            .header("Content-Type", "application/json")
            .body("{\"relatedPartyId\":\"" + directorParty + "\",\"relationType\":\"SIGNATORY\",\"ownershipPercentage\":null}")
            .exchange().expectBody(byte[].class).returnResult();

        assertEquals(201, signatory.getStatus().value(), new String(signatory.getResponseBody()));
        var roles = mapper.relationsOf(directorParty);
        assertEquals(java.util.List.of("DIRECTOR", "SIGNATORY"), roles.stream().map(row -> row.get("relation_type")).toList());

        var share = http.patch().uri("/client-onboarding/v1/applications/{id}/relations/{r}", applicationId, uboRelation.get("relation_id"))
            .headers(this::client).header("Content-Type", "application/json")
            .body("{\"dirtyFields\":[\"ownershipPercentage\"],\"values\":{\"ownershipPercentage\":\"60\"}}")
            .exchange().expectBody(byte[].class).returnResult();

        assertEquals(200, share.getStatus().value(), new String(share.getResponseBody()));
        assertEquals(0, new java.math.BigDecimal("60").compareTo((java.math.BigDecimal)mapper.relationsOf(uboParty).getFirst()
            .get("ownership_percentage")));
        assertEquals(2, draft(applicationId).path("people").size());
    }

    /** 关系问题都带字段路径：非持股角色填比例、比例越界、辖区外关系类型、缺关系体；均不建人员。 */
    @Test
    void test_relationErrorsHaveFields() {
        long applicationId = started("ORGANIZATION", "AE", "LLC", null);

        assertField(people(applicationId, person("{\"relationType\":\"DIRECTOR\",\"ownershipPercentage\":\"10\"}")),
            "relation.ownershipPercentage");
        assertField(people(applicationId, person("{\"relationType\":\"UBO\",\"ownershipPercentage\":\"150\"}")),
            "relation.ownershipPercentage");
        assertField(people(applicationId, person("{\"relationType\":\"ZZ_MANAGER\"}")), "relation.relationType");
        assertField(people(applicationId, person("{\"relationType\":\"UBO\",\"effectiveFrom\":\"2024-13-40\"}")), "relation.effectiveFrom");
        assertField(people(applicationId, "{\"person\":{\"dirtyFields\":[\"individual.givenName\"],\"values\":{\"individual\":"
            + "{\"givenName\":\"Ali\"}}}}"), "relation");
        assertEquals(0, draft(applicationId).path("people").size());
    }

    /** 422 INVALID_DRAFT 且 errors[0].field 为指定路径。 */
    void assertField(EntityExchangeResult<byte[]> result, String field) {
        assertEquals(422, result.getStatus().value(), new String(result.getResponseBody()));
        assertEquals("INVALID_DRAFT", body(result).path("code").asString());
        assertEquals(field, body(result).path("errors").get(0).path("field").asString(), new String(result.getResponseBody()));
    }

    /** 最小人员体加指定关系。 */
    static String person(String relation) {
        return "{\"person\":{\"dirtyFields\":[\"individual.givenName\"],\"values\":{\"individual\":{\"givenName\":\"Ali\"}}},"
            + "\"relation\":" + relation + "}";
    }

    /** 以页面原样的 JSON 体新建人员。 */
    EntityExchangeResult<byte[]> people(long applicationId, String json) {
        return http.post().uri("/client-onboarding/v1/applications/{id}/people", applicationId).headers(this::client)
            .header("Content-Type", "application/json").body(json.replace("\n", "")).exchange().expectBody(byte[].class).returnResult();
    }
}
