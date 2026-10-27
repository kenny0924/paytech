package com.paytech.client.master.auto.ext.http.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.client.master.api.checkout.ClientMasterCheckoutApi;
import com.paytech.client.master.api.enums.AcquiringQualification;
import com.paytech.client.master.api.enums.CheckoutCandidateErrorCode;
import com.paytech.client.master.api.enums.PartnerRoleCode;
import com.paytech.client.master.test.base.checkout.AbstractCheckoutCandidateApiTest;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 两段式解析第二段：外部资格检查后复核根版本与选定授权（design/06 §2.1、§4），以及签署校验的参与方资料读取。
 * @see ClientMasterCheckoutApi#verify(String, com.paytech.client.master.api.checkout.request.VerifyCheckoutCandidateRequestBody)
 * @see ClientMasterCheckoutApi#partyProfiles(String, java.util.List)
 * @see com.paytech.client.master.api.entityadmin.ClientMasterEntityAdminApi
 */
class VerifyCandidatesHttpTest extends AbstractCheckoutCandidateApiTest {
    /** 两次快照之间没有变化时返回 unchanged=true。 */
    @Test
    void test_unchanged() {
        var first = post(CANDIDATES, request(), headers()).assertOk().json();
        post(VERIFY, verification(first, 0), headers()).assertOk().assertBoolean("/unchanged", true)
            .assertTexts("/changedRoots");
    }

    /** OE 根修订推进后返回变化的根，不再评估候选。 */
    @Test
    void test_rootChanged() {
        var first = post(CANDIDATES, request(), headers()).assertOk().json();
        data.advanceEntityRoot("AE992");
        post(VERIFY, verification(first, 0), headers()).assertOk().assertBoolean("/unchanged", false)
            .assertTexts("/changedRoots", "OPERATING_ENTITY:AE992");
    }

    /** 根未变但选定授权在当前时点不再可用（来源关系结束）时返回候选变化。 */
    @Test
    void test_selectedAuthorizationUnavailable() {
        var first = post(CANDIDATES, request(), headers()).assertOk().json();
        data.endFirstSource();
        post(VERIFY, verification(first, 0), headers()).assertOk().assertBoolean("/unchanged", false)
            .assertTexts("/changedRoots", "CANDIDATE:AE991");
    }

    /** 回传的候选摘要与当前事实不一致时视为候选变化。 */
    @Test
    void test_digestMismatch() {
        var first = post(CANDIDATES, request(), headers()).assertOk().json();
        var body = verification(first, 1);
        ((ObjectNode) body.path("candidate")).put("candidateDigest", "0".repeat(64));
        post(VERIFY, body, headers()).assertOk().assertBoolean("/unchanged", false).assertTexts("/changedRoots", "CANDIDATE:AE992");
    }

    /** 运营修改 OE 收单资质推进 OE 根版本，复核返回该根变化。 */
    @Test
    void test_acquiringQualificationChanged() {
        var first = post(CANDIDATES, request(), headers()).assertOk().json();
        var body = json.createObjectNode().put("acquiringQualification", AcquiringQualification.MERCHANT.getCode())
            .put("expectedChangeVersion", 1).put("reason", "Downgraded by acquirer");
        assertEquals(200, invoker.exchange(HttpMethod.PUT, "/client-master-admin/v1/operating-entities/AE991/acquiring-qualification",
            admin(), body).getStatus().value());
        post(VERIFY, verification(first, 0), headers()).assertOk().assertBoolean("/unchanged", false)
            .assertTexts("/changedRoots", "OPERATING_ENTITY:AE991");
    }

    /** 运营修改提供方角色推进 Partner 根版本，复核返回该根变化。 */
    @Test
    void test_providerRolesChanged() {
        var first = post(CANDIDATES, request(), headers()).assertOk().json();
        var body = json.createObjectNode().put("expectedChangeVersion", 1).put("reason", "Referral programme");
        body.putArray("roles").add(PartnerRoleCode.OE_PROVIDER.getCode()).add(PartnerRoleCode.REFERRER.getCode());
        assertEquals(200, invoker.exchange(HttpMethod.PUT, "/client-master-admin/v1/local-partners/910000000000150/roles", admin(),
            body).getStatus().value());
        post(VERIFY, verification(first, 0), headers()).assertOk().assertBoolean("/unchanged", false)
            .assertTexts("/changedRoots", "LOCAL_PARTNER:910000000000150");
    }

    /** Gateway 可信操作者头。 */
    private HttpHeaders admin() {
        var headers = TestRequestHeaders.internal(REQUEST_ID);
        headers.set("X-Gateway-Actor-Subject", "9300000019");
        headers.set("X-Gateway-Session-Id", "ops-session-1");
        return headers;
    }

    /** 按 ID 读取参与方资料；格式非法的 ID 在读取前拒绝。 */
    @Test
    void test_partyProfiles() {
        get(PROFILES + "?ids=910000000000111&ids=910000000000005&ids=910000000000999", headers()).assertOk()
            .assertText("/items/0/profileId", "910000000000005").assertText("/items/1/profileId", "910000000000111")
            .assertText("/items/1/partyId", "910000000000102").assertText("/items/1/approvedTime", "2026-09-01T00:00:00.000000Z")
            .assertText("/items/1/partyType", "ORGANIZATION").assertText("/items/1/incorporationCountry", "AE");
        get(PROFILES + "?ids=01", headers()).assertProblem(422, CheckoutCandidateErrorCode.INVALID_CANDIDATE_QUERY.getCode());
    }

    /** 由首次响应构造复核请求，选定第 index 个候选。 */
    private JsonNode verification(JsonNode first, int index) {
        var candidate = first.path("candidates").get(index);
        ObjectNode selected = json.createObjectNode().put("entityNo", candidate.path("entityNo").asString())
            .put("affiliationId", candidate.path("affiliationId").asString())
            .put("priorityNo", candidate.path("priorityNo").asInt()).put("scopeId", candidate.path("scopeId").asString())
            .put("sourceRelationId", candidate.path("sourceRelation").path("sourceRelationId").asString())
            .put("candidateDigest", candidate.path("candidateDigest").asString());
        ObjectNode body = json.createObjectNode();
        body.set("query", json.valueToTree(request()));
        body.set("rootVersions", first.path("rootVersions"));
        body.set("candidate", selected);
        return body;
    }
}
