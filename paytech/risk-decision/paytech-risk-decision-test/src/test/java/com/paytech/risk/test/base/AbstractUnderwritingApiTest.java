package com.paytech.risk.test.base;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.control.core.enums.UnderwritingOutcome;
import com.paytech.risk.decision.api.control.request.UnderwritingDecisionBody;
import com.paytech.risk.decision.api.control.request.UnderwritingInputBody;
import com.paytech.risk.test.builder.control.UnderwritingRequestBuilder;
import com.paytech.risk.test.checker.control.UnderwritingResponseChecker;
import com.paytech.risk.test.mapper.control.UnderwritingTestMapper;
import com.paytech.risk.test.mocker.control.UnderwritingRuleMocker;

/**
 * 承保接口测试的公共入口辅助：以指定 Operations 账号发出真实 HTTP 请求，返回承保 Checker
 * <li>不含 @Test；承保版本与测试规则由 {@link UnderwritingRuleMocker} 在全局 AfterEach 回收。</li>
 *
 * @author Claude
 */
public abstract class AbstractUnderwritingApiTest extends RiskDecisionMockTestBase {
    /** 承保员账号。 */
    protected static final long UNDERWRITER = 70101L;

    /** 风控负责人账号（复核人）。 */
    protected static final long RISK_LEAD = 70102L;

    /** 承保接口根路径。 */
    protected static final String ROOT = "/risk-underwriting/v1";

    @Autowired
    protected UnderwritingTestMapper store;

    @Autowired
    protected UnderwritingRuleMocker rules;

    /** 本用例独有的 Client ID。 */
    protected static long newClientId() {
        return ThreadLocalRandom.current().nextLong(10_000_000L, 99_999_999L);
    }

    /** 以指定账号发 GET。 */
    protected UnderwritingResponseChecker get(long actor, String path) {
        return send(HttpMethod.GET, actor, path, null);
    }

    /** 以指定账号发 POST。 */
    protected UnderwritingResponseChecker post(long actor, String path, Object body) {
        return send(HttpMethod.POST, actor, path, body);
    }

    /** 以指定账号发 PUT。 */
    protected UnderwritingResponseChecker put(long actor, String path, Object body) {
        return send(HttpMethod.PUT, actor, path, body);
    }

    /** 承保员为 Client 建草稿。 */
    protected UnderwritingResponseChecker createDraft(long clientId, UnderwritingInputBody input,
        UnderwritingDecisionBody decision) {
        return post(UNDERWRITER, ROOT + "/clients/" + clientId + "/underwritings",
            UnderwritingRequestBuilder.create(input, decision));
    }

    /** 承保员提交草稿。 */
    protected UnderwritingResponseChecker submit(long underwritingId, int version, UnderwritingInputBody input,
        UnderwritingDecisionBody decision, UnderwritingOutcome outcome, String declineReason) {
        return post(UNDERWRITER, ROOT + "/underwritings/" + underwritingId + "/submission",
            UnderwritingRequestBuilder.submit(version, input, decision, outcome, declineReason));
    }

    /** 建草稿并以给定输入批准，返回批准后的 Checker。 */
    protected UnderwritingResponseChecker approved(long clientId, UnderwritingInputBody input) {
        UnderwritingResponseChecker draft = createDraft(clientId, input, UnderwritingRequestBuilder.decision());
        return submit(draft.id(), draft.version(), input, UnderwritingRequestBuilder.decision(), UnderwritingOutcome.APPROVE,
            null);
    }

    /** Gateway 转发的 Operations 可信身份及新的 Request ID。 */
    protected static HttpHeaders headers(long actor) {
        HttpHeaders headers = TestRequestHeaders.internal("risk-underwriting-" + UUID.randomUUID());
        headers.set("X-Gateway-Actor-Subject", Long.toString(actor));
        headers.set("X-Gateway-Session-Id", "risk-underwriting-session");
        return headers;
    }

    /** 发出真实 HTTP 请求。 */
    private UnderwritingResponseChecker send(HttpMethod method, long actor, String path, Object body) {
        HttpHeaders headers = headers(actor);
        return new UnderwritingResponseChecker(http.exchange(method, path, headers, body), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }
}
