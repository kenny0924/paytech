package com.paytech.platform.support.auto.ext.http.client;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.platform.support.accesscontrol.domain.enums.AuthenticationRealm;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengePurpose;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengeStatus;
import com.paytech.platform.support.api.authentication.ClientAuthenticationApi;
import com.paytech.platform.support.api.authentication.request.EmailRequestBody;
import com.paytech.platform.support.api.authentication.request.LinkRequestBody;
import com.paytech.platform.support.api.authentication.request.PasswordResetRequestBody;
import com.paytech.platform.support.api.authentication.request.RegisterRequestBody;
import com.paytech.platform.support.test.base.AuthenticationEmailTestBase;
import com.paytech.platform.support.test.notification.NotificationFixture;

/**
 * 密码重置申请：已验证账号签发重置挑战，经 Delivery 即时发送重置邮件；邮件中的链接可完成重置。
 *
 * @see ClientAuthenticationApi#requestPasswordReset(EmailRequestBody)
 */
class ClientPasswordResetRequestTest extends AuthenticationEmailTestBase {

    private static final String EMAIL = "active-client@example.test";

    /** 注册 → 用验证邮件链接激活 → 申请重置：收到重置邮件，用其链接重置成功，挑战被消费。 */
    @Test
    void test_normalFlow() {
        loginEmailVault.allow(EMAIL);
        ses.accept(EMAIL, "ses-verification-1").accept(EMAIL, "ses-reset-1");
        anonymous(HttpMethod.POST, "/authentication/v1/client/register",
            new RegisterRequestBody(EMAIL, "Registration-Passphrase-2026")).assertStatus(202);
        String[] verification = link(ses.requests().getFirst());
        anonymous(HttpMethod.POST, "/authentication/v1/client/email-verifications",
            new LinkRequestBody(verification[0], verification[1])).assertStatus(200);

        anonymous(HttpMethod.POST, "/authentication/v1/client/password-reset-requests",
            new EmailRequestBody(EMAIL)).assertStatus(202);

        var mail = ses.requests().get(1);
        String[] reset = link(mail);
        anonymous(HttpMethod.POST, "/authentication/v1/client/password-resets",
            new PasswordResetRequestBody(reset[0], reset[1], "Reset-Passphrase-2026-New")).assertStatus(204);
        long accountId = ((Number) operationsData.accounts(AuthenticationRealm.CUSTOMER.getCode()).getFirst()
            .get("account_id")).longValue();
        var resetChallenge = operationsData.challenges(accountId).stream()
            .filter(c -> ClientChallengePurpose.PASSWORD_RESET.getCode().equals(c.get("purpose"))).toList();
        assertAll(() -> assertEquals(2, ses.requests().size()),
            () -> assertEquals(EMAIL, mail.recipient()),
            () -> assertEquals(NotificationFixture.RESET_SUBJECT, mail.subject()),
            () -> assertTrue(mail.text().contains(PUBLIC_ORIGIN + "/reset-password#challengeNo="), "链接指向重置页"),
            () -> assertEquals(1, resetChallenge.size()),
            () -> assertEquals(ClientChallengeStatus.CONSUMED.getCode(), resetChallenge.getFirst().get("status")));
        assertNothingPersisted();
    }

    /** 未注册邮箱返回同一受理结果，不发信。 */
    @Test
    void test_noDataFound() {
        loginEmailVault.allow(EMAIL);

        anonymous(HttpMethod.POST, "/authentication/v1/client/password-reset-requests",
            new EmailRequestBody(EMAIL)).assertStatus(202);

        assertTrue(ses.requests().isEmpty());
    }
}
