package com.paytech.platform.support.auto.ext.http.client;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.accesscontrol.domain.enums.AuthenticationRealm;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengePurpose;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengeStatus;
import com.paytech.platform.support.api.authentication.ClientAuthenticationApi;
import com.paytech.platform.support.api.authentication.request.RegisterRequestBody;
import com.paytech.platform.support.api.authentication.response.AcceptedResponseBody;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;
import com.paytech.platform.support.test.base.AuthenticationEmailTestBase;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.external.LoginEmailVaultMocker;
import com.paytech.platform.support.test.notification.NotificationFixture;

/**
 * Client 注册入口：在写事务中创建 CUSTOMER 账号与注册验证挑战，提交后经 Delivery 即时发送验证邮件。
 *
 * @see ClientAuthenticationApi#register(RegisterRequestBody)
 */
@ExtendWith(OutputCaptureExtension.class)
class ClientRegistrationTest extends AuthenticationEmailTestBase {

    private static final String PATH = "/authentication/v1/client/register";

    private static final String PASSWORD = "Registration-Passphrase-2026";

    /** 新邮箱：202 受理，账号与 PENDING 注册挑战同事务落库；SES 收到一封验证邮件，链接、秘密和邮箱不进日志、不落通知表。 */
    @Test
    void test_normalFlow(CapturedOutput output) {
        String email = "new-client@example.test";
        loginEmailVault.allow(email);
        ses.accept(email, "ses-verification-1");
        double acceptedBefore = accepted();

        var accepted = register(email).assertStatus(202).as(AcceptedResponseBody.class);

        Map<String, Object> account = onlyCustomer();
        List<Map<String, Object>> challenges = operationsData.challenges(((Number) account.get("account_id")).longValue());
        var mail = onlySesRequest();
        String[] link = link(mail);
        assertAll(() -> assertTrue(accepted.isAccepted()),
            () -> assertEquals(LoginEmailVaultMocker.tokenOf(email), account.get("email_vault_token")),
            () -> assertEquals(1, challenges.size()),
            () -> assertEquals(ClientChallengePurpose.SIGN_UP_EMAIL.getCode(), challenges.getFirst().get("purpose")),
            () -> assertEquals(ClientChallengeStatus.PENDING.getCode(), challenges.getFirst().get("status")),
            () -> assertEquals(email, mail.recipient()),
            () -> assertEquals(FROM_ADDRESS, mail.from()),
            () -> assertEquals(NotificationFixture.VERIFICATION_SUBJECT, mail.subject()),
            () -> assertTrue(mail.text().contains(PUBLIC_ORIGIN + "/verify-email#challengeNo="), "邮件链接指向验证页"),
            () -> assertEquals(1, vault.credentialRevealCount(), "发送前读取一次 SES 凭据"),
            () -> assertEquals(1, accepted() - acceptedBefore),
            () -> assertFalse(output.getAll().contains(link[1]), "链接秘密不得进入日志"),
            () -> assertFalse(output.getAll().contains(email), "收件邮箱不得进入日志"),
            () -> assertEquals(1, loginEmailVault.protectCount()));
        assertNothingPersisted();
    }

    /** 投递失败不回滚已提交的账号与挑战，仍返回同一受理结果，只按原因计失败指标、不重试。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("deliveryFailures")
    void test_emailDeliveryFailure(String reason, Consumer<ClientRegistrationTest> failure, int sesRequests,
        CapturedOutput output) {
        String email = "mail-down@example.test";
        loginEmailVault.allow(email);
        failure.accept(this);
        double failedBefore = failed(reason);

        register(email).assertStatus(202);

        Map<String, Object> account = onlyCustomer();
        assertAll(() -> assertEquals(1, operationsData.challenges(((Number) account.get("account_id")).longValue()).size()),
            () -> assertEquals(sesRequests, ses.requests().size(), "只发送一次，不重试"),
            () -> assertEquals(1, failed(reason) - failedBefore),
            () -> assertFalse(output.getAll().contains("secret="), "链接不得进入日志"),
            () -> assertFalse(output.getAll().contains(email), "收件邮箱不得进入日志"));
        assertNothingPersisted();
    }

    /** 每组只引入一种失败：SES 明确拒绝、SES 断连、Vault 凭据不可用、模板停用。 */
    static Stream<Arguments> deliveryFailures() {
        String email = "mail-down@example.test";
        return Stream.of(
            Arguments.of("REJECTED", (Consumer<ClientRegistrationTest>) test ->
                test.ses.fail(email, 400, "MessageRejected", "Email address is not verified: " + email), 1),
            Arguments.of("UNKNOWN", (Consumer<ClientRegistrationTest>) test -> test.ses.disconnect(email), 1),
            Arguments.of("DEPENDENCY", (Consumer<ClientRegistrationTest>) test -> test.vault.credentialUnavailable(), 0),
            Arguments.of("CONFIGURATION", (Consumer<ClientRegistrationTest>) test ->
                test.notificationData.changeVerificationTemplate(NotificationConfigurationStatus.DISABLED.getCode()), 0));
    }

    /** 非法邮箱在调用 Vault 与写库前拒绝，也不发信。 */
    @Test
    void test_invalidParameter() {
        register("not-an-email").assertProblem(400, CommonErrorCode.INVALID_REQUEST);

        assertAll(() -> assertEquals(0, loginEmailVault.protectCount(), "校验失败不调用 Vault"),
            () -> assertTrue(operationsData.accounts(AuthenticationRealm.CUSTOMER.getCode()).isEmpty()),
            () -> assertTrue(ses.requests().isEmpty()));
    }

    private PlatformResponseChecker register(String email) {
        return anonymous(HttpMethod.POST, PATH, new RegisterRequestBody(email, PASSWORD));
    }

    private Map<String, Object> onlyCustomer() {
        List<Map<String, Object>> accounts = operationsData.accounts(AuthenticationRealm.CUSTOMER.getCode());
        assertEquals(1, accounts.size(), "只创建一个 CUSTOMER 账号");
        return accounts.getFirst();
    }
}
