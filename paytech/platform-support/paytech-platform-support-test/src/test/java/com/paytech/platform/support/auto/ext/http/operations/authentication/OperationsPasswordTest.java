package com.paytech.platform.support.auto.ext.http.operations.authentication;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;

import com.paytech.platform.support.api.authentication.OperationsAuthenticationApi;
import com.paytech.platform.support.api.authentication.request.ChangePasswordRequestBody;
import com.paytech.platform.support.api.authentication.request.FirstAdministratorRequestBody;
import com.paytech.platform.support.api.authentication.request.OperationsLoginRequestBody;
import com.paytech.platform.support.api.authentication.response.AccountCreatedResponseBody;
import com.paytech.platform.support.test.base.OperationsApiTestBase;
import com.paytech.platform.support.test.builder.OperationsCatalog;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;

/**
 * 本人改密与初始化账号交接首个管理员。
 *
 * @see OperationsAuthenticationApi#changePassword(String, String, ChangePasswordRequestBody)
 * @see OperationsAuthenticationApi#createFirstAdministrator(String, String, FirstAdministratorRequestBody)
 */
class OperationsPasswordTest extends OperationsApiTestBase {

    private static final String BASE = "/authentication/v1/operations";
    private static final String NEW_PASSWORD = "Another-Strong-Secret-42";

    /** 改密推进凭据版本、清除强制改密并撤销全部会话；新密码可登录、旧密码失效。 */
    @Test
    void test_changePasswordRevokesSessions() {
        Operator operator = operationsMocker.operator(OperatorBuilder.administrator().forceChange(true));
        long otherSession = OperatorBuilder.nextId();
        operationsData.insertSession(otherSession, operator.accountId(), "OPERATIONS", "OPERATIONS", "TENANT",
            OperationsCatalog.INTERNAL_TENANT_ID, operator.memberId(), "ACTIVE",
            operationsMocker.now().plusSeconds(1800), operationsMocker.now().plusSeconds(28800),
            operationsMocker.now());

        change(operator, operator.password(), NEW_PASSWORD).assertNoContent();

        Map<String, Object> password = operationsData.password(operator.accountId());
        assertAll(() -> assertEquals(2, ((Number)password.get("credential_version")).intValue()),
            () -> assertEquals(Boolean.FALSE, toBoolean(password.get("force_change_flag"))), () -> assertEquals(2,
                ((Number)operationsData.account(operator.accountId()).get("credential_version")).intValue()));
        operationsData.sessions(operator.accountId())
            .forEach(session -> assertEquals("REVOKED", session.get("status")));
        assertEquals("PASSWORD_CHANGED", operationsData.session(otherSession).get("revocation_reason"));
        assertEquals("REVOKED", operationsData.refreshes(operator.sessionId()).getFirst().get("status"));
        assertEquals(1, loginEmailVault.revealCount(), "改密需读取邮箱以拒绝包含账号的密码");
        auditChecker.assertEvent(requestId, "OPERATIONS_PASSWORD_CHANGE", "SUCCEEDED", operator.actor(), "USER_ACCOUNT",
            operator.actor());

        login(operator.email(), operator.password()).assertProblem(401, "OPERATIONS_AUTHENTICATION_FAILED");
        login(operator.email(), NEW_PASSWORD).assertOk();
    }

    /** 当前密码错误不改变凭据与会话。 */
    @Test
    void test_wrongCurrentPasswordRejected() {
        Operator operator = operationsMocker.operator(OperatorBuilder.administrator());

        change(operator, "Wrong-Current-Passw0rd", NEW_PASSWORD).assertProblem(401, "OPERATIONS_AUTHENTICATION_FAILED");

        assertEquals(1, ((Number)operationsData.password(operator.accountId()).get("credential_version")).intValue());
        assertEquals("ACTIVE", operationsData.session(operator.sessionId()).get("status"));
    }

    /** 新密码不满足规则时拒绝。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("weakPasswords")
    void test_weakNewPasswordRejected(String scenario, String newPassword) {
        Operator operator = operationsMocker.operator(OperatorBuilder.administrator().email("weak-owner@example.test"));

        change(operator, operator.password(), newPassword).assertProblem(400, "BUSINESS_CHECK_FAILED");

        assertEquals(1, ((Number)operationsData.password(operator.accountId()).get("credential_version")).intValue());
    }

    static Stream<Arguments> weakPasswords() {
        return Stream.of(Arguments.of("长度不足 15", "Short-Pass-1"), Arguments.of("常见密码", "correcthorsebatterystaple"),
            Arguments.of("包含服务名", "my-paytech-password-1"), Arguments.of("包含登录账号", "weak-owner-password-123"),
            Arguments.of("与当前密码相同", OperatorBuilder.PASSWORD));
    }

    /** 已改密的初始化账号创建首个管理员：新账号须改初始密码，初始化账号永久停用。 */
    @Test
    void test_createFirstAdministrator() {
        Operator bootstrap = operationsMocker.operator(OperatorBuilder.bootstrap().forceChange(false));
        String email = "first-admin@example.test";
        loginEmailVault.allow(email);

        var created = administrator(bootstrap, email, NEW_PASSWORD).assertOk().as(AccountCreatedResponseBody.class);

        long accountId = Long.parseLong(created.getAccountId());
        Map<String, Object> account = operationsData.account(accountId);
        assertAll(() -> assertEquals("HUMAN", account.get("account_type")),
            () -> assertEquals("ACTIVE", account.get("status")),
            () -> assertEquals("f***@example.test", account.get("email_masked")),
            () -> assertEquals(Boolean.TRUE, toBoolean(operationsData.password(accountId).get("force_change_flag"))));
        var member = operationsData.members(accountId);
        assertEquals(1, member.size());
        assertEquals(created.getMemberId(), member.getFirst().get("member_id").toString());
        var grants = operationsData.memberRoles(Long.parseLong(created.getMemberId()));
        assertEquals(1, grants.size());
        assertEquals(OperationsCatalog.ADMIN_ROLE_ID, ((Number)grants.getFirst().get("role_id")).longValue());
        Map<String, Object> retired = operationsData.account(bootstrap.accountId());
        assertAll(() -> assertEquals("DISABLED", retired.get("status")),
            () -> assertEquals("REVOKED", operationsData.password(bootstrap.accountId()).get("status")),
            () -> assertEquals("BOOTSTRAP_COMPLETED",
                operationsData.session(bootstrap.sessionId()).get("revocation_reason")));
        auditChecker.assertEvent(requestId, "OPERATIONS_BOOTSTRAP_COMPLETE", "SUCCEEDED", bootstrap.actor(),
            "USER_ACCOUNT", created.getAccountId());
        login("admin", bootstrap.password()).assertProblem(401, "OPERATIONS_AUTHENTICATION_FAILED");
    }

    /** 初始化账号未改初始密码、已有人员账号或调用者不是初始化账号时拒绝。 */
    @Test
    void test_firstAdministratorPreconditions() {
        Operator unchanged = operationsMocker.operator(OperatorBuilder.bootstrap());
        loginEmailVault.allow("first-admin@example.test");
        administrator(unchanged, "first-admin@example.test", NEW_PASSWORD).assertProblem(400, "BUSINESS_CHECK_FAILED");

        operationsData.updatePassword(unchanged.accountId(), "ACTIVE", false);
        Operator human = operationsMocker.operator(OperatorBuilder.administrator());
        administrator(unchanged, "first-admin@example.test", NEW_PASSWORD).assertProblem(400, "BUSINESS_CHECK_FAILED");
        administrator(human, "first-admin@example.test", NEW_PASSWORD).assertProblem(400, "BUSINESS_CHECK_FAILED");

        assertEquals(1, operationsData.accounts("OPERATIONS").stream()
            .filter(row -> "HUMAN".equals(row.get("account_type"))).count(), "失败不创建账号");
        assertEquals("ACTIVE", operationsData.account(unchanged.accountId()).get("status"));
    }

    /** 请求体缺少必填字段在进入业务前被拒绝。 */
    @Test
    void test_invalidParameter() {
        Operator operator = operationsMocker.operator(OperatorBuilder.administrator());

        change(operator, "", NEW_PASSWORD).assertProblem(422, "VALIDATION_ERROR").assertFieldError("currentPassword");
        administrator(operator, null, NEW_PASSWORD).assertProblem(422, "VALIDATION_ERROR").assertFieldError("email");
    }

    private PlatformResponseChecker change(Operator operator, String current, String next) {
        return post(BASE + "/password", operator, new ChangePasswordRequestBody(current, next));
    }

    private PlatformResponseChecker administrator(Operator operator, String email, String password) {
        return post(BASE + "/bootstrap/administrator", operator, new FirstAdministratorRequestBody(email, password));
    }

    private PlatformResponseChecker login(String identifier, String password) {
        return anonymous(HttpMethod.POST, BASE + "/login", new OperationsLoginRequestBody(identifier, password));
    }

    private static Boolean toBoolean(Object value) {
        return value instanceof Boolean flag ? flag : ((Number)value).intValue() != 0;
    }
}
