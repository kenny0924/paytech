package com.paytech.platform.support.auto.ext.http.internalauth;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.nimbusds.jwt.SignedJWT;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.internalauth.api.InternalTokenInvalidException;
import com.paytech.platform.support.api.internalauth.InternalTokenIssueApi;
import com.paytech.platform.support.api.internalauth.request.InternalTokenIssueRequestBody;
import com.paytech.platform.support.api.internalauth.response.InternalTokenIssueResponseBody;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Result;
import com.paytech.platform.support.auditsecurity.domain.enums.AuditActorType;
import com.paytech.platform.support.test.base.MockTestBase;
import com.paytech.platform.support.test.checker.AuditChecker;
import com.paytech.platform.support.test.dal.AuditTestMapper;
import com.paytech.platform.support.test.util.InternalTokens;

/**
 * 内部调用 Token 签发：经真实 Controller、调用方解析、Processor、内存签名与审计写入；以目标服务视角用 Foundation 校验器验签。
 * <li>未登记调用方、越权目标与越权 Purpose 返回同一业务拒绝，写 DENIED 审计与安全事件，不返回 Token。</li>
 * <li>参数不合法在 Processor 之前拒绝，不写审计。</li>
 *
 * @see InternalTokenIssueApi#issue(String, InternalTokenIssueRequestBody)
 */
class InternalTokenIssueTest extends MockTestBase {

    private static final String CALLER = "issuer-test";
    private static final String TARGET = "payment-service";
    private static final String ACTION = "INTERNAL_TOKEN_ISSUE";

    @Autowired
    InternalTokens tokens;

    @Autowired
    AuditChecker audits;

    @Autowired
    AuditTestMapper auditData;

    /** 白名单调用方获得 300 秒 Token：声明、签名与审计均与请求一致，审计不含 Token。 */
    @Test
    void test_normalFlow() throws Exception {
        String requestId = "plt-itk-" + UUID.randomUUID();
        Instant before = Instant.now().minusSeconds(1);

        var body = tokens.request(CALLER, body(TARGET, "CARD_READ"), requestId).assertOk()
            .as(InternalTokenIssueResponseBody.class);

        var identity = tokens.verifyAs(body.getToken(), TARGET);
        var jwt = SignedJWT.parse(body.getToken());
        var claims = jwt.getJWTClaimsSet();
        assertAll(() -> assertEquals(CALLER, identity.caller()),
            () -> assertEquals("CARD_READ", identity.purpose()),
            () -> assertTrue(identity.verified()),
            () -> assertEquals("platform-internal-test-1", jwt.getHeader().getKeyID()),
            () -> assertEquals(List.of(TARGET), claims.getAudience()),
            () -> assertEquals(InternalTokens.INSTANCE_ID, claims.getStringClaim("inst")),
            () -> assertEquals(Duration.ofSeconds(300),
                Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant())),
            () -> assertEquals(claims.getExpirationTime().toInstant(), Instant.parse(body.getExpiresAt())),
            () -> assertFalse(claims.getIssueTime().toInstant().isBefore(before.truncatedTo(java.time.temporal.ChronoUnit.SECONDS))));
        var event = audits.assertEvent(requestId, ACTION, Result.SUCCEEDED.getCode(), CALLER, "INTERNAL_TOKEN",
            identity.jti());
        assertEquals(AuditActorType.WORKLOAD.getCode(), event.get("actor_type"));
        long eventId = ((Number) event.get("audit_event_id")).longValue();
        assertEquals(Map.of("target_service", TARGET, "purpose", "CARD_READ"), attributes(eventId));
        audits.assertNoSecret(event, body.getToken());
        assertEquals(0, auditData.securityEventCount(ACTION));
    }

    /** Token 只对申请的目标服务有效：其他服务按自身服务名校验时拒绝。 */
    @Test
    void test_tokenBoundToTarget() {
        String token = tokens.issue(CALLER, TARGET);

        assertThrows(InternalTokenInvalidException.class, () -> tokens.verifyAs(token, "client-master-service"));
    }

    /** 每次签发新的 jti，不复用旧 Token。 */
    @Test
    void test_eachIssueHasNewTokenId() {
        var first = tokens.verifyAs(tokens.issue(CALLER, TARGET), TARGET);
        var second = tokens.verifyAs(tokens.issue(CALLER, TARGET), TARGET);

        assertFalse(first.jti().equals(second.jti()));
    }

    /** 未登记调用方、越权目标、越权 Purpose：同一业务拒绝，写 DENIED 审计与安全事件。 */
    @ParameterizedTest
    @MethodSource("deniedRequests")
    void test_businessRuleViolation(String caller, String target, String purpose) {
        String requestId = "plt-itk-" + UUID.randomUUID();

        var checker = tokens.request(caller, body(target, purpose), requestId)
            .assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED);

        assertFalse(checker.text().contains("eyJ"), "拒绝响应不得包含 Token");
        audits.assertEvent(requestId, ACTION, Result.DENIED.getCode(), caller, "INTERNAL_TOKEN", "DENIED");
        assertEquals(1, auditData.securityEventCount(ACTION));
    }

    /** 拒绝场景：调用方未登记；目标服务未授权；Purpose 未授权。 */
    static Stream<Arguments> deniedRequests() {
        return Stream.of(Arguments.of("unknown-service", TARGET, "GENERAL"),
            Arguments.of(CALLER, "risk-compliance-service", "GENERAL"),
            Arguments.of(CALLER, TARGET, "LEDGER_WRITE"));
    }

    /** 缺少调用方 Header 或调用方名不合法：400；Body 字段格式不合法：422 字段错误；均不进入签发，不写审计。 */
    @Test
    void test_invalidParameter() {
        String missingCaller = "plt-itk-" + UUID.randomUUID();
        tokens.request(null, body(TARGET, "GENERAL"), missingCaller).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        String badTarget = "plt-itk-" + UUID.randomUUID();
        tokens.request(CALLER, body("Payment_Service", "GENERAL"), badTarget)
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("targetService");
        String badPurpose = "plt-itk-" + UUID.randomUUID();
        tokens.request(CALLER, body(TARGET, "general"), badPurpose)
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("purpose");
        String badCaller = "plt-itk-" + UUID.randomUUID();
        tokens.request("Issuer_Test", body(TARGET, "GENERAL"), badCaller)
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);

        assertEquals(0, auditData.eventCount());
    }

    /** 签发请求体。 */
    private static Map<String, Object> body(String target, String purpose) {
        var body = new HashMap<String, Object>();
        body.put("targetService", target);
        body.put("purpose", purpose);
        return body;
    }

    /** 审计属性键值。 */
    private Map<String, Object> attributes(long eventId) {
        var values = new HashMap<String, Object>();
        auditData.attributes(eventId).forEach(row -> values.put((String) row.get("attr_key"), row.get("value_text")));
        return values;
    }
}
