package com.paytech.platform.support.auto.ext.http.operations.authentication;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.platform.support.api.authentication.OperationsAuthenticationApi;
import com.paytech.platform.support.api.authentication.request.OperationsRefreshRequestBody;
import com.paytech.platform.support.api.authentication.response.AuthenticationTokensResponseBody;
import com.paytech.platform.support.api.authentication.response.OperationsContextResponseBody;
import com.paytech.platform.support.test.base.OperationsApiTestBase;
import com.paytech.platform.support.test.builder.OperationsCatalog;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.util.TestAuthenticationKeys;

/**
 * Operations 会话生命周期：刷新代轮换与重用撤销、退出、当前上下文及会话失效。
 *
 * @see OperationsAuthenticationApi#refresh(OperationsRefreshRequestBody)
 * @see OperationsAuthenticationApi#logout(String, String, OperationsRefreshRequestBody)
 * @see OperationsAuthenticationApi#context(String, String)
 */
class OperationsSessionTest extends OperationsApiTestBase {

    private static final String BASE = "/authentication/v1/operations";

    @Autowired
    private TestAuthenticationKeys keys;

    /** 刷新消费当前代并签发唯一后继，会话代号推进。 */
    @Test
    void test_refreshRotatesGeneration() {
        Operator operator = operationsMocker.operator(OperatorBuilder.administrator());

        var tokens = refresh(operator.refreshToken()).assertOk().as(AuthenticationTokensResponseBody.class);

        assertNotEquals(operator.refreshToken(), tokens.getRefreshToken());
        var generations = operationsData.refreshes(operator.sessionId());
        assertEquals(2, generations.size());
        assertAll(() -> assertEquals("CONSUMED", generations.get(0).get("status")),
            () -> assertNull(generations.get(0).get("active_slot")),
            () -> assertEquals(generations.get(1).get("refresh_generation_id"),
                generations.get(0).get("replaced_by_generation_id")),
            () -> assertEquals("ISSUED", generations.get(1).get("status")),
            () -> assertEquals(2, ((Number)generations.get(1).get("generation_no")).intValue()), () -> assertEquals(2,
                ((Number)operationsData.session(operator.sessionId()).get("current_generation_no")).intValue()));
        var jwt = keys.verifiedJwt("OPERATIONS", tokens.getAccessToken());
        assertEquals(operator.session(), jwt[1].path("sid").asString());
        auditChecker.assertEvent(requestId, "OPERATIONS_REFRESH", "SUCCEEDED", operator.actor(), "AUTH_SESSION",
            operator.session());
    }

    /** 已消费的旧代再次使用判定为泄露，撤销整个会话及后继。 */
    @Test
    void test_reusedRefreshCompromisesSession() {
        Operator operator = operationsMocker.operator(OperatorBuilder.administrator());
        var next = refresh(operator.refreshToken()).assertOk().as(AuthenticationTokensResponseBody.class);

        refresh(operator.refreshToken()).assertProblem(401, "OPERATIONS_SESSION_INVALID");

        Map<String, Object> session = operationsData.session(operator.sessionId());
        assertAll(() -> assertEquals("COMPROMISED", session.get("status")),
            () -> assertEquals("REFRESH_REUSE", session.get("revocation_reason")));
        auditChecker.assertEvent(requestId, "OPERATIONS_REFRESH", "DENIED", operator.actor(), "AUTH_SESSION",
            operator.session());
        refresh(next.getRefreshToken()).assertProblem(401, "OPERATIONS_SESSION_INVALID");
    }

    /** 未知或格式错误的刷新令牌统一拒绝，不改变任何会话。 */
    @Test
    void test_unknownRefreshRejected() {
        Operator operator = operationsMocker.operator(OperatorBuilder.administrator());
        String forged = operator.refreshToken().substring(0, 23) + "A".repeat(43);

        refresh(forged).assertProblem(401, "OPERATIONS_SESSION_INVALID");
        refresh("not-a-refresh-token").assertProblem(401, "OPERATIONS_SESSION_INVALID");
        auditChecker.assertEvent(requestId, "OPERATIONS_REFRESH", "DENIED", "UNKNOWN", "AUTH_SESSION", "UNKNOWN");

        assertEquals("ACTIVE", operationsData.session(operator.sessionId()).get("status"));
        assertEquals("ISSUED", operationsData.refreshes(operator.sessionId()).getFirst().get("status"));
    }

    /** 账号已停用时刷新撤销会话并拒绝。 */
    @Test
    void test_refreshAfterAccountDisabledRevokesSession() {
        Operator operator = operationsMocker.operator(OperatorBuilder.administrator());
        operationsData.updateAccountSecurity(operator.accountId(), "DISABLED", null, 1, 1);

        refresh(operator.refreshToken()).assertProblem(401, "OPERATIONS_SESSION_INVALID");

        Map<String, Object> session = operationsData.session(operator.sessionId());
        assertAll(() -> assertEquals("REVOKED", session.get("status")),
            () -> assertEquals("SESSION_INVALID", session.get("revocation_reason")),
            () -> assertEquals("REVOKED", operationsData.refreshes(operator.sessionId()).getFirst().get("status")));
    }

    /** 持有同一会话的刷新秘密才能退出；退出撤销会话与刷新代。 */
    @Test
    void test_logoutRevokesSession() {
        Operator operator = operationsMocker.operator(OperatorBuilder.administrator());

        call(HttpMethod.POST, BASE + "/logout", headers(operator),
            new OperationsRefreshRequestBody(operator.refreshToken())).assertNoContent();

        Map<String, Object> session = operationsData.session(operator.sessionId());
        assertAll(() -> assertEquals("REVOKED", session.get("status")),
            () -> assertEquals("LOGOUT", session.get("revocation_reason")),
            () -> assertEquals("REVOKED", operationsData.refreshes(operator.sessionId()).getFirst().get("status")));
        auditChecker.assertEvent(requestId, "OPERATIONS_LOGOUT", "SUCCEEDED", operator.actor(), "AUTH_SESSION",
            operator.session());
        context(operator).assertProblem(401, "OPERATIONS_SESSION_INVALID");
    }

    /** 他人会话的刷新秘密或账号不匹配时拒绝退出，原会话保留。 */
    @Test
    void test_logoutRequiresOwnRefreshSecret() {
        Operator operator = operationsMocker.operator(OperatorBuilder.administrator());
        Operator other = operationsMocker.operator(OperatorBuilder.administrator());

        call(HttpMethod.POST, BASE + "/logout", headers(operator),
            new OperationsRefreshRequestBody(other.refreshToken())).assertProblem(401, "OPERATIONS_SESSION_INVALID");
        call(HttpMethod.POST, BASE + "/logout", headers(other.actor(), operator.session()),
            new OperationsRefreshRequestBody(operator.refreshToken())).assertProblem(401, "OPERATIONS_SESSION_INVALID");

        assertEquals("ACTIVE", operationsData.session(operator.sessionId()).get("status"));
        assertEquals("ACTIVE", operationsData.session(other.sessionId()).get("status"));
    }

    /** 当前上下文按本库事实返回，不包含邮箱原文。 */
    @Test
    void test_contextReflectsCurrentFacts() {
        Operator operator = operationsMocker.operator(OperatorBuilder.administrator());

        var checker = context(operator).assertOk();
        var context = checker.as(OperationsContextResponseBody.class);

        assertAll(() -> assertEquals(operator.actor(), context.getAccountId()),
            () -> assertEquals(operator.session(), context.getSessionId()),
            () -> assertEquals("HUMAN", context.getAccountType()),
            () -> assertEquals(operator.email().charAt(0) + "***@example.test", context.getEmailMasked()),
            () -> assertEquals("ACTIVE", context.getAccountStatus()),
            () -> assertEquals(Long.toString(OperationsCatalog.INTERNAL_TENANT_ID), context.getTenantId()),
            () -> assertEquals(Long.toString(operator.memberId()), context.getMemberId()),
            () -> assertFalse(context.isPasswordChangeRequired()),
            () -> assertFalse(context.isBootstrapAdministratorRequired()),
            () -> assertEquals("DEVELOPMENT_MOCK", context.getIntegrationMode()),
            () -> assertFalse(checker.text().contains(operator.email()), "响应不得包含邮箱原文"));
        assertEquals(0, loginEmailVault.revealCount(), "上下文查询不读取邮箱原文");
    }

    /** 初始化账号的上下文没有租户成员，并给出首次管理员提示。 */
    @Test
    void test_bootstrapContext() {
        Operator bootstrap = operationsMocker.operator(OperatorBuilder.bootstrap().forceChange(false));

        var context = context(bootstrap).assertOk().as(OperationsContextResponseBody.class);

        assertAll(() -> assertEquals("BOOTSTRAP_ADMIN", context.getAccountType()),
            () -> assertNull(context.getTenantId()), () -> assertNull(context.getMemberId()),
            () -> assertTrue(context.isBootstrapAdministratorRequired()));
    }

    /** 会话撤销、账号纪元变化、成员暂停或身份 Header 非法时，上下文查询统一失效。 */
    @Test
    void test_contextRejectsInvalidSession() {
        Operator revoked = operationsMocker.operator(OperatorBuilder.administrator().sessionStatus("REVOKED"));
        context(revoked).assertProblem(401, "OPERATIONS_SESSION_INVALID");

        Operator rotated = operationsMocker.operator(OperatorBuilder.administrator());
        operationsData.updateAccountSecurity(rotated.accountId(), "ACTIVE", null, 2, 1);
        context(rotated).assertProblem(401, "OPERATIONS_SESSION_INVALID");

        Operator suspended = operationsMocker.operator(OperatorBuilder.administrator());
        operationsData.updateMemberStatus(suspended.memberId(), "SUSPENDED");
        context(suspended).assertProblem(401, "OPERATIONS_SESSION_INVALID");

        Operator expired = operationsMocker.operator(OperatorBuilder.administrator());
        operationsData.updateSession(expired.sessionId(), "ACTIVE", operationsMocker.now(),
            operationsMocker.now().plusSeconds(3600));
        context(expired).assertProblem(401, "OPERATIONS_SESSION_INVALID");

        call(HttpMethod.GET, BASE + "/context", headers(revoked.actor(), rotated.session()), null).assertProblem(401,
            "OPERATIONS_SESSION_INVALID");
        call(HttpMethod.GET, BASE + "/context", headers("0", "abc"), null).assertProblem(400, "INVALID_REQUEST");
    }

    private PlatformResponseChecker refresh(String token) {
        return anonymous(HttpMethod.POST, BASE + "/refresh", new OperationsRefreshRequestBody(token));
    }

    private PlatformResponseChecker context(Operator operator) {
        return get(BASE + "/context", operator);
    }
}
