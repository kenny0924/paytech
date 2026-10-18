package com.paytech.client.master.auto.ext.http.checkout;

import org.junit.jupiter.api.Test;

import com.paytech.client.master.api.checkout.ClientMasterCheckoutApi;
import com.paytech.client.master.api.enums.CheckoutCandidateErrorCode;
import com.paytech.client.master.test.base.checkout.AbstractCheckoutCandidateApiTest;

/**
 * 两段式解析第一段：只依赖主档事实的候选、排序、落选、根版本与只读保证（design/06 §2.1）。
 * @see ClientMasterCheckoutApi#candidates(String, com.paytech.client.master.api.checkout.request.CheckoutCandidatesRequestBody)
 */
class ResolveCandidatesHttpTest extends AbstractCheckoutCandidateApiTest {
    /** 两个 OE 均满足主档条件时按 Affiliation 优先级返回，并携带法律原始记录与查询范围。 */
    @Test
    void test_orderedCandidates() {
        post(CANDIDATES, request(), headers()).assertOk().assertCandidates("AE991", "AE992")
            .assertFirstCandidateLegal(3, 1).assertText("/client/status", "ACTIVE")
            .assertText("/client/partyProfileId", "910000000000005")
            .assertBoolean("/client/storedCardSetting/enabled", false)
            .assertText("/classification/mccCode", "5734")
            .assertTexts("/commercialScope/entityNos", "AE991", "AE992").assertTexts("/commercialScope/localPartnerIds");
    }

    /** Scope 全部结束后没有候选，仍返回空数组而不是错误。 */
    @Test
    void test_noCandidates() {
        data.endScopes();
        post(CANDIDATES, request(), headers()).assertOk().assertCandidates();
    }

    /** 币种与批准的 Scope 不精确匹配时该 OE 落选。 */
    @Test
    void test_scopeMismatchHasNoCandidate() {
        var request = request();
        request.setCurrency("GBP");
        post(CANDIDATES, request, headers()).assertOk().assertCandidates();
    }

    /** 唯一有效来源关系失效的 OE 落选，其他 OE 不受影响。 */
    @Test
    void test_inactiveSourceDropsCandidate() {
        data.endFirstSource();
        post(CANDIDATES, request(), headers()).assertOk().assertCandidates("AE992");
    }

    /** 根版本随候选返回，供 commercial 复核。 */
    @Test
    void test_rootVersionsReturned() {
        data.advanceEntityRoot("AE992");
        post(CANDIDATES, request(), headers()).assertOk().assertRoot("client", "910000000000001", 1)
            .assertRoot("operatingEntities", "AE991", 1).assertRoot("operatingEntities", "AE992", 2);
    }

    /** 候选查询不修改主档、不登记补偿任务。 */
    @Test
    void test_readOnly() {
        fixture.assertReadOnly(() -> post(CANDIDATES, request(), headers()).assertOk().assertCandidates("AE991", "AE992"));
    }

    /** Client 主体未批准或分类未就绪整体拒绝；Scope 审批冲突报配置冲突。 */
    @Test
    void test_clientNotEligibleAndContextAmbiguous() {
        data.clientApproval(null);
        post(CANDIDATES, request(), headers()).assertProblem(422, CheckoutCandidateErrorCode.CLIENT_NOT_ELIGIBLE.getCode());
        data.clientApproval(java.time.Instant.parse("2026-09-01T00:00:00Z"));
        data.primaryClassification(null);
        post(CANDIDATES, request(), headers()).assertProblem(422, CheckoutCandidateErrorCode.CLIENT_NOT_ELIGIBLE.getCode());
        data.primaryClassification(1);
        data.rejectScopeApplication();
        post(CANDIDATES, request(), headers()).assertProblem(409,
            CheckoutCandidateErrorCode.CANDIDATE_CONTEXT_AMBIGUOUS.getCode());
    }

    /** 非 commercial 调用与不存在的 Client 不可区分；格式非法的查询在读取前拒绝。 */
    @Test
    void test_callerAndQueryBoundary() {
        var other = headers();
        other.set("X-Caller-Service", "commerce-service");
        post(CANDIDATES, request(), other).assertProblem(404, CheckoutCandidateErrorCode.CLIENT_NOT_FOUND.getCode());
        var missing = request();
        missing.setClientId("9223372036854775806");
        post(CANDIDATES, missing, headers()).assertProblem(404, CheckoutCandidateErrorCode.CLIENT_NOT_FOUND.getCode());
        var invalid = request();
        invalid.setCurrency("usd");
        post(CANDIDATES, invalid, headers()).assertProblem(422, CheckoutCandidateErrorCode.INVALID_CANDIDATE_QUERY.getCode());
    }

    /** 未知持久化枚举使读取失败关闭为 503，不泄露数据库值或 Java 类型。 */
    @Test
    void test_unknownPersistedStatusFailsClosed() {
        data.unknownClientStatus();
        post(CANDIDATES, request(), headers())
            .assertProblem(503, CheckoutCandidateErrorCode.CANDIDATE_SOURCE_UNAVAILABLE.getCode())
            .assertDoesNotExpose("UNKNOWN_STATUS").assertDoesNotExpose("ClientStatus");
    }
}
