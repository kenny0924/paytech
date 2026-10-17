package com.paytech.platform.support.auto.ext.http.operations.authentication;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.platform.support.api.authentication.OperationsAuthenticationApi;
import com.paytech.platform.support.api.authentication.request.OperationsLoginRequestBody;
import com.paytech.platform.support.api.authentication.response.AuthenticationTokensResponseBody;
import com.paytech.platform.support.test.base.OperationsApiTestBase;
import com.paytech.platform.support.test.builder.OperationsCatalog;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.util.TestAuthenticationKeys;

/**
 * Operations 登录：成功签发会话、Bootstrap 提示、失败计数与锁定、各类账号状态拒绝及参数校验。
 *
 * @see OperationsAuthenticationApi#login(OperationsLoginRequestBody)
 */
class OperationsLoginTest extends OperationsApiTestBase {

    private static final String PATH = "/authentication/v1/operations/login";

    @Autowired
    private TestAuthenticationKeys keys;

    /** 邮箱登录签发 Access 与首个刷新代，会话绑定内部租户成员，审计记录成功。 */
    @Test
    void test_loginSucceeds() {
        Operator operator = operationsMocker.operator(OperatorBuilder.administrator().withoutSession());

        var tokens = login(operator.email(), operator.password()).assertOk().as(AuthenticationTokensResponseBody.class);

        assertAll(() -> assertEquals("Bearer", tokens.getTokenType()), () -> assertEquals(900, tokens.getExpiresIn()),
            () -> assertTrue(tokens.getRefreshToken().matches("[A-Za-z0-9_-]{22}\\.[A-Za-z0-9_-]{43}")),
            () -> assertTrue(tokens.getRefreshExpiresIn() > 28_000 && tokens.getRefreshExpiresIn() <= 28_800),
            () -> assertFalse(tokens.isPasswordChangeRequired()),
            () -> assertFalse(tokens.isBootstrapAdministratorRequired()));
        var sessions = operationsData.sessions(operator.accountId());
        assertEquals(1, sessions.size());
        Map<String, Object> session = sessions.getFirst();
        long sessionId = ((Number)session.get("session_id")).longValue();
        assertAll(() -> assertEquals("ACTIVE", session.get("status")),
            () -> assertEquals("TENANT", session.get("context_type")),
            () -> assertEquals(OperationsCatalog.INTERNAL_TENANT_ID, ((Number)session.get("tenant_id")).longValue()),
            () -> assertEquals(operator.memberId(), ((Number)session.get("tenant_member_id")).longValue()),
            () -> assertEquals(1, ((Number)session.get("current_generation_no")).intValue()));
        var refreshes = operationsData.refreshes(sessionId);
        assertEquals(1, refreshes.size());
        assertAll(() -> assertEquals("ISSUED", refreshes.getFirst().get("status")),
            () -> assertEquals(tokens.getRefreshToken().substring(0, 22), refreshes.getFirst().get("token_lookup_id")),
            () -> assertEquals(keys.hmac("OPERATIONS", "operations-refresh-v1", tokens.getRefreshToken()),
                refreshes.getFirst().get("token_secret_hmac")));
        var jwt = keys.verifiedJwt("OPERATIONS", tokens.getAccessToken());
        assertAll(() -> assertEquals(OperationsCatalog.OPERATIONS_KID, jwt[0].path("kid").asString()),
            () -> assertEquals(operator.actor(), jwt[1].path("sub").asString()),
            () -> assertEquals(Long.toString(sessionId), jwt[1].path("sid").asString()),
            () -> assertEquals("TENANT", jwt[1].path("context_type").asString()),
            () -> assertEquals(Long.toString(operator.memberId()), jwt[1].path("tenant_member_id").asString()),
            () -> assertFalse(jwt[1].has("email"), "Access 不得携带邮箱"));
        assertEquals(1, loginEmailVault.protectCount());
        var event = auditChecker.assertEvent(requestId, "OPERATIONS_LOGIN", "SUCCEEDED", operator.actor(),
            "AUTH_SESSION", Long.toString(sessionId));
        auditChecker.assertNoSecret(event, operator.password());
    }

    /** 初始化账号以 admin 登录，未改初始密码时只提示改密，会话为 BOOTSTRAP 上下文。 */
    @Test
    void test_bootstrapLoginRequiresPasswordChange() {
        Operator bootstrap = operationsMocker.operator(OperatorBuilder.bootstrap().withoutSession());

        var tokens = login("admin", bootstrap.password()).assertOk().as(AuthenticationTokensResponseBody.class);

        assertTrue(tokens.isPasswordChangeRequired());
        assertFalse(tokens.isBootstrapAdministratorRequired());
        Map<String, Object> session = operationsData.sessions(bootstrap.accountId()).getFirst();
        assertAll(() -> assertEquals("BOOTSTRAP", session.get("context_type")),
            () -> assertNull(session.get("tenant_id")), () -> assertNull(session.get("tenant_member_id")));
        var jwt = keys.verifiedJwt("OPERATIONS", tokens.getAccessToken());
        assertFalse(jwt[1].has("tenant_id"), "BOOTSTRAP 会话不携带租户");
        assertEquals(0, loginEmailVault.protectCount(), "admin 登录名不调用 Vault");
    }

    /** 初始化账号改密后仍须先创建首个管理员。 */
    @Test
    void test_bootstrapAfterPasswordChangeRequiresAdministrator() {
        Operator bootstrap = operationsMocker.operator(OperatorBuilder.bootstrap().forceChange(false).withoutSession());

        var tokens = login("admin", bootstrap.password()).assertOk().as(AuthenticationTokensResponseBody.class);

        assertFalse(tokens.isPasswordChangeRequired());
        assertTrue(tokens.isBootstrapAdministratorRequired());
    }

    /** 密码错误先提交失败计数再统一拒绝，不建立会话。 */
    @Test
    void test_wrongPasswordCountsFailure() {
        Operator operator = operationsMocker.operator(OperatorBuilder.administrator().withoutSession());

        login(operator.email(), "Wrong-Passw0rd-Value!").assertProblem(401, "OPERATIONS_AUTHENTICATION_FAILED");

        Map<String, Object> account = operationsData.account(operator.accountId());
        assertAll(() -> assertEquals(1, ((Number)account.get("consecutive_password_failures")).intValue()),
            () -> assertNull(account.get("locked_until")),
            () -> assertNotNull(account.get("last_password_failure_time")),
            () -> assertTrue(operationsData.sessions(operator.accountId()).isEmpty()));
        auditChecker.assertEvent(requestId, "OPERATIONS_LOGIN", "DENIED", operator.actor(), "USER_ACCOUNT",
            operator.actor());
    }

    /** 连续第五次失败锁定 30 分钟；锁定期内正确密码也被拒绝，成功登录前计数不清零。 */
    @Test
    void test_fifthFailureLocksAccount() {
        Operator operator = operationsMocker.operator(OperatorBuilder.administrator().withoutSession());
        for (int attempt = 0; attempt < 5; attempt++) {
            login(operator.email(), "Wrong-Passw0rd-Value!").assertProblem(401, "OPERATIONS_AUTHENTICATION_FAILED");
        }
        Map<String, Object> account = operationsData.account(operator.accountId());
        Instant lockedUntil =
            ((java.time.LocalDateTime)account.get("locked_until")).toInstant(java.time.ZoneOffset.UTC);
        assertEquals(5, ((Number)account.get("consecutive_password_failures")).intValue());
        assertEquals(operationsMocker.now().plusSeconds(1800), lockedUntil, "按应用时钟锁定 30 分钟");

        login(operator.email(), operator.password()).assertProblem(401, "OPERATIONS_AUTHENTICATION_FAILED");

        assertTrue(operationsData.sessions(operator.accountId()).isEmpty());
        assertEquals(5,
            ((Number)operationsData.account(operator.accountId()).get("consecutive_password_failures")).intValue(),
            "锁定期内的拒绝不再累计");
    }

    /** 成功登录清零此前的失败计数。 */
    @Test
    void test_successResetsFailures() {
        Operator operator = operationsMocker.operator(OperatorBuilder.administrator().withoutSession());
        login(operator.email(), "Wrong-Passw0rd-Value!").assertProblem(401, "OPERATIONS_AUTHENTICATION_FAILED");

        login(operator.email(), operator.password()).assertOk();

        Map<String, Object> account = operationsData.account(operator.accountId());
        assertEquals(0, ((Number)account.get("consecutive_password_failures")).intValue());
        assertNull(account.get("last_password_failure_time"));
    }

    /** 未知邮箱与非邮箱标识统一拒绝，不暴露账号是否存在。 */
    @Test
    void test_unknownIdentifierRejected() {
        String unknown = "nobody@example.test";
        loginEmailVault.allow(unknown);

        login(unknown, OperatorBuilder.PASSWORD).assertProblem(401, "OPERATIONS_AUTHENTICATION_FAILED");
        auditChecker.assertEvent(requestId, "OPERATIONS_LOGIN", "DENIED", "UNKNOWN", "USER_ACCOUNT", "UNKNOWN");
        login("not-an-email", OperatorBuilder.PASSWORD).assertProblem(401, "OPERATIONS_AUTHENTICATION_FAILED");

        assertEquals(1, loginEmailVault.protectCount(), "非法邮箱格式不调用 Vault");
    }

    /** 账号、凭据、成员或租户不可用时统一拒绝，不累计密码失败，不建立会话。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("unavailableAccounts")
    void test_unavailableAccountRejected(String scenario, OperatorBuilder builder, boolean suspendTenant) {
        Operator operator = operationsMocker.operator(builder.withoutSession());
        if (suspendTenant) {
            operationsData.updateTenantStatus(OperationsCatalog.INTERNAL_TENANT_ID, "ACTIVE", "SUSPENDED");
        }

        login(operator.email(), operator.password()).assertProblem(401, "OPERATIONS_AUTHENTICATION_FAILED");

        assertTrue(operationsData.sessions(operator.accountId()).isEmpty());
        assertEquals(0,
            ((Number)operationsData.account(operator.accountId()).get("consecutive_password_failures")).intValue());
    }

    static Stream<Arguments> unavailableAccounts() {
        return Stream.of(Arguments.of("账号已停用", OperatorBuilder.administrator().accountStatus("DISABLED"), false),
            Arguments.of("账号锁定中", OperatorBuilder.administrator().lockedUntil(Instant.parse("2099-01-01T00:00:00Z")),
                false),
            Arguments.of("密码凭据已撤销", OperatorBuilder.administrator().passwordStatus("REVOKED"), false),
            Arguments.of("无内部租户成员", OperatorBuilder.administrator().withoutMember(), false),
            Arguments.of("成员已暂停", OperatorBuilder.administrator().memberStatus("SUSPENDED"), false),
            Arguments.of("租户安全暂停", OperatorBuilder.administrator(), true));
    }

    /** 缺少必填字段在进入业务前被拒绝，不调用 Vault。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void test_invalidParameter(String field, OperationsLoginRequestBody body) {
        anonymous(HttpMethod.POST, PATH, body).assertProblem(422, "VALIDATION_ERROR").assertFieldError(field);

        assertEquals(0, loginEmailVault.protectCount());
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(Arguments.of("loginIdentifier", new OperationsLoginRequestBody(" ", "x")),
            Arguments.of("password", new OperationsLoginRequestBody("admin", null)),
            Arguments.of("loginIdentifier", new OperationsLoginRequestBody("a".repeat(321), "x")));
    }

    private PlatformResponseChecker login(String identifier, String password) {
        return anonymous(HttpMethod.POST, PATH, new OperationsLoginRequestBody(identifier, password));
    }
}
