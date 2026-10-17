package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.client.contract.api.enums.ClientDraftErrorCode;
import com.paytech.client.master.domain.enums.PartyRelationType;
import com.paytech.client.master.test.base.onboarding.AbstractSubmissionApiTest;
import com.paytech.client.master.test.mocker.onboarding.OnboardingApplicationMocker.ApplicationFixture;

import tools.jackson.databind.json.JsonMapper;

/**
 * 请求幂等由 Gateway 处理后，同一写请求重复到达 CCT 时的业务防重：关系与材料关联返回原记录，删除重复执行仍为 204。
 *
 * @see com.paytech.client.contract.api.onboarding.ClientPeopleApi#createRelation
 * @see com.paytech.client.contract.api.onboarding.ClientPeopleApi#removeRelation
 * @see com.paytech.client.contract.api.onboarding.ClientDocumentApi#associate
 * @see com.paytech.client.contract.api.onboarding.ClientDocumentApi#remove
 */
class OnboardingDuplicateWriteTest extends AbstractSubmissionApiTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 本类公共配置：三类资格的通用材料要求。 */
    @BeforeEach
    void setUpDuplicateMocks() {
        mockGenericRequirements();
    }

    /** C09：相同组织、人员、类型且内容相同返回原关系；内容不同仍 409。 */
    @Test
    void test_createSameRelation() {
        var app = applications.organization(ACCOUNT_ID);
        var person = applications.person(app, PartyRelationType.UBO.getCode());

        var same = createRelation(app, person.partyId(), "40");

        assertEquals(201, same.getStatus().value(), new String(same.getResponseBody()));
        assertEquals(Long.toString(person.relationId()), body(same).path("relationId").asString());
        assertTrue(body(same).path("commandId").isNull());

        var different = createRelation(app, person.partyId(), "50");

        assertEquals(409, different.getStatus().value());
        assertEquals(ClientDraftErrorCode.RELATION_ALREADY_EXISTS.getCode(), body(different).path("code").asString());
        assertEquals(app.applicationId(), mapper.relationApplication(person.relationId()));
    }

    /** C11：删除关系后再次删除仍为 204。 */
    @Test
    void test_removeRelationTwice() {
        var app = applications.organization(ACCOUNT_ID);
        var person = applications.person(app, PartyRelationType.UBO.getCode());

        assertEquals(204, removeRelation(app, person.relationId()).getStatus().value());
        assertEquals(204, removeRelation(app, person.relationId()).getStatus().value());
    }

    /** C12：同一申请、主体、要求、类型和文件重复关联返回原引用，不新增。 */
    @Test
    void test_associateSameDocument() {
        var app = applications.organization(ACCOUNT_ID);
        long fileId = applications.nextId();
        owners.mockFile(fileId, HASH, "AVAILABLE");

        var first = associate(app, fileId);
        var again = associate(app, fileId);

        assertEquals(201, first.getStatus().value(), new String(first.getResponseBody()));
        assertEquals(201, again.getStatus().value(), new String(again.getResponseBody()));
        assertEquals(body(first).path("documentId").asString(), body(again).path("documentId").asString());
        assertEquals(1, mapper.documentCount(app.applicationId()));
    }

    /** C13：删除材料后再次删除仍为 204。 */
    @Test
    void test_removeDocumentTwice() {
        var app = applications.organization(ACCOUNT_ID);
        long documentId = availableDocument(app, app.partyId(), "REGISTRATION", "REGISTRATION_CERTIFICATE");

        assertEquals(204, removeDocument(app, documentId).getStatus().value());
        assertEquals(204, removeDocument(app, documentId).getStatus().value());
        assertEquals(0, mapper.documentCount(app.applicationId()));
    }

    /** 通过真实 HTTP 调用 C09。 */
    EntityExchangeResult<byte[]> createRelation(ApplicationFixture app, long partyId, String ownership) {
        Map<String, String> relation = new HashMap<>();
        relation.put("relatedPartyId", Long.toString(partyId));
        relation.put("relationType", PartyRelationType.UBO.getCode());
        relation.put("ownershipPercentage", ownership);
        relation.put("effectiveFrom", "2024-01-01");
        relation.put("effectiveTo", null);
        return http.post().uri("/client-onboarding/v1/applications/{id}/relations", app.applicationId()).headers(this::client)
            .header("Content-Type", "application/json").body(JSON.writeValueAsString(relation))
            .exchange().expectBody(byte[].class).returnResult();
    }

    /** 通过真实 HTTP 调用 C11。 */
    EntityExchangeResult<byte[]> removeRelation(ApplicationFixture app, long relationId) {
        return http.delete().uri("/client-onboarding/v1/applications/{id}/relations/{relationId}", app.applicationId(), relationId)
            .headers(this::client).exchange().expectBody(byte[].class).returnResult();
    }

    /** 通过真实 HTTP 调用 C12，关联组织注册证明。 */
    EntityExchangeResult<byte[]> associate(ApplicationFixture app, long fileId) {
        var document = Map.of("subjectPartyId", Long.toString(app.partyId()), "requirementCode", "REGISTRATION",
            "documentType", "REGISTRATION_CERTIFICATE", "fileId", Long.toString(fileId));
        return http.post().uri("/client-onboarding/v1/applications/{id}/documents", app.applicationId()).headers(this::client)
            .header("Content-Type", "application/json").body(JSON.writeValueAsString(document))
            .exchange().expectBody(byte[].class).returnResult();
    }

    /** 通过真实 HTTP 调用 C13。 */
    EntityExchangeResult<byte[]> removeDocument(ApplicationFixture app, long documentId) {
        return http.delete().uri("/client-onboarding/v1/applications/{id}/documents/{documentId}", app.applicationId(), documentId)
            .headers(this::client).exchange().expectBody(byte[].class).returnResult();
    }

    /** 可信 Client BFF 调用头。 */
    void client(HttpHeaders headers) {
        headers.set("X-Caller-Service", "client-console-bff");
        headers.set("X-Gateway-Actor-Subject", Long.toString(ACCOUNT_ID));
        headers.set("X-Gateway-Session-Id", Long.toString(SESSION_ID));
        headers.set("X-Request-Id", "cct-duplicate-write-test");
    }
}
