package com.paytech.platform.support.auto.ext.http.client;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.accesscontrol.domain.enums.AuthenticationRealm;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengePurpose;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengeStatus;
import com.paytech.platform.support.api.authentication.ClientAuthenticationApi;
import com.paytech.platform.support.api.authentication.request.RegisterRequestBody;
import com.paytech.platform.support.api.authentication.response.AcceptedResponseBody;
import com.paytech.platform.support.test.base.OperationsApiTestBase;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.external.LoginEmailVaultMocker;

/**
 * Client 注册入口：在写事务中创建 CUSTOMER 账号与注册验证挑战，提交后投递验证邮件。
 *
 * @see ClientAuthenticationApi#register(RegisterRequestBody)
 */
class ClientRegistrationTest extends OperationsApiTestBase {

    private static final String PATH = "/authentication/v1/client/register";

    private static final String PASSWORD = "Registration-Passphrase-2026";

    /** 新邮箱：202 受理，账号与 PENDING 注册挑战同事务落库，邮件链接指向验证页。 */
    @Test
    void test_normalFlow() {
        String email = "new-client@example.test";
        loginEmailVault.allow(email);

        var accepted = register(email).assertStatus(202).as(AcceptedResponseBody.class);

        Map<String, Object> account = onlyCustomer();
        List<Map<String, Object>> challenges = operationsData.challenges(((Number) account.get("account_id")).longValue());
        var mail = smtp.nextMail();
        assertAll(() -> assertTrue(accepted.isAccepted()),
            () -> assertEquals(LoginEmailVaultMocker.tokenOf(email), account.get("email_vault_token")),
            () -> assertEquals(1, challenges.size()),
            () -> assertEquals(ClientChallengePurpose.SIGN_UP_EMAIL.getCode(), challenges.getFirst().get("purpose")),
            () -> assertEquals(ClientChallengeStatus.PENDING.getCode(), challenges.getFirst().get("status")),
            () -> assertEquals(email, mail.recipient()),
            () -> assertTrue(mail.body().contains("/verify-email#challengeNo="), "邮件链接指向验证页"),
            () -> assertEquals(1, loginEmailVault.protectCount()));
    }

    /** 邮件投递失败不回滚已提交的账号与挑战，仍返回同一受理结果，客户可重发。 */
    @Test
    void test_emailDeliveryFailure() {
        String email = "mail-down@example.test";
        loginEmailVault.allow(email);
        smtp.rejectNextMessage();

        register(email).assertStatus(202);

        Map<String, Object> account = onlyCustomer();
        assertEquals(1, operationsData.challenges(((Number) account.get("account_id")).longValue()).size());
        assertEquals(0, smtp.pendingCount(), "被拒绝的邮件不进入收件队列");
    }

    /** 非法邮箱在调用 Vault 与写库前拒绝。 */
    @Test
    void test_invalidParameter() {
        register("not-an-email").assertProblem(400, CommonErrorCode.INVALID_REQUEST);

        assertAll(() -> assertEquals(0, loginEmailVault.protectCount(), "校验失败不调用 Vault"),
            () -> assertTrue(operationsData.accounts(AuthenticationRealm.CUSTOMER.getCode()).isEmpty()));
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
