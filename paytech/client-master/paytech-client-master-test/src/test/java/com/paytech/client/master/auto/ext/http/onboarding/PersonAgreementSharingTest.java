package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.client.contract.api.enums.ClientDraftErrorCode;
import com.paytech.client.master.domain.enums.PartyRelationType;
import com.paytech.client.master.test.base.onboarding.AbstractSubmissionApiTest;
import com.paytech.client.master.test.mocker.onboarding.OnboardingApplicationMocker.ApplicationFixture;
import com.paytech.client.master.test.mocker.onboarding.OnboardingApplicationMocker.PersonFixture;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 人员“共享”标记中的合同引用改由 commercial 提供（design/06 §2.2，原 ClientPeopleMapper 读取 t_agreement_party）。
 * <li>被合同引用或 commercial 不可用时失败关闭：人员不可编辑，保存返回 PERSON_NOT_EDITABLE。</li>
 *
 * @see com.paytech.client.contract.api.onboarding.ClientDraftApi#read
 * @see com.paytech.client.contract.api.onboarding.ClientPeopleApi#savePerson
 */
class PersonAgreementSharingTest extends AbstractSubmissionApiTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 公共基线下人员可编辑；被合同引用后读取与保存都视为共享。 */
    @Test
    void test_referencedPersonIsShared() {
        var app = applications.organization(ACCOUNT_ID);
        var person = applications.person(app, PartyRelationType.UBO.getCode());
        assertTrue(editable(read(app), person));

        agreementParties.mockReferencedParty(person.partyId());

        assertEquals(false, editable(read(app), person));
        var saved = savePerson(app, person);
        assertEquals(409, saved.getStatus().value(), new String(saved.getResponseBody()));
        assertEquals(ClientDraftErrorCode.PERSON_NOT_EDITABLE.getCode(), body(saved).path("code").asString());
    }

    /** commercial 不可用时不放行修改。 */
    @Test
    void test_commercialUnavailableFailsClosed() {
        var app = applications.organization(ACCOUNT_ID);
        var person = applications.person(app, PartyRelationType.UBO.getCode());
        agreementParties.mockUnavailable();

        assertEquals(false, editable(read(app), person));
        var saved = savePerson(app, person);
        assertEquals(409, saved.getStatus().value(), new String(saved.getResponseBody()));
        assertEquals(ClientDraftErrorCode.PERSON_NOT_EDITABLE.getCode(), body(saved).path("code").asString());
    }

    /** 读取草稿中该人员的可编辑标记。 */
    private boolean editable(EntityExchangeResult<byte[]> draft, PersonFixture person) {
        assertEquals(200, draft.getStatus().value(), new String(draft.getResponseBody()));
        JsonNode people = body(draft).path("people");
        return people.valueStream().filter(p -> Long.toString(person.partyId()).equals(p.path("partyId").asString()))
            .findFirst().orElseThrow().path("editable").asBoolean();
    }

    /** 通过真实 HTTP 读取草稿。 */
    private EntityExchangeResult<byte[]> read(ApplicationFixture app) {
        return http.get().uri("/client-onboarding/v1/applications/{id}", app.applicationId())
            .header("X-Caller-Service", "client-console-bff").header("X-Gateway-Actor-Subject", Long.toString(ACCOUNT_ID))
            .header("X-Gateway-Session-Id", Long.toString(SESSION_ID)).header("X-Request-Id", "cct-sharing-test")
            .exchange().expectBody(byte[].class).returnResult();
    }

    /** 通过真实 HTTP 保存人员的名字。 */
    private EntityExchangeResult<byte[]> savePerson(ApplicationFixture app, PersonFixture person) {
        var patch = Map.of("dirtyFields", List.of("individual.givenName"),
            "values", Map.of("individual", Map.of("givenName", "Shared")));
        return http.patch().uri("/client-onboarding/v1/applications/{id}/people/{partyId}", app.applicationId(), person.partyId())
            .header("X-Caller-Service", "client-console-bff").header("X-Gateway-Actor-Subject", Long.toString(ACCOUNT_ID))
            .header("X-Gateway-Session-Id", Long.toString(SESSION_ID)).header("X-Request-Id", "cct-sharing-test")
            .header("Content-Type", "application/json")
            .body(JSON.writeValueAsString(patch)).exchange().expectBody(byte[].class).returnResult();
    }
}
