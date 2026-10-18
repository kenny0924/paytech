package com.paytech.platform.support.auto.ext.http.client;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.platform.support.accesscontrol.domain.enums.AuthenticationRealm;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengeStatus;
import com.paytech.platform.support.api.authentication.ClientAuthenticationApi;
import com.paytech.platform.support.api.authentication.request.EmailRequestBody;
import com.paytech.platform.support.api.authentication.request.RegisterRequestBody;
import com.paytech.platform.support.test.base.AuthenticationEmailTestBase;
import com.paytech.platform.support.test.notification.NotificationFixture;

/**
 * 重新发送注册验证：冷却过后签发新挑战并让旧挑战失效，经 Delivery 即时发送新的验证邮件。
 *
 * @see ClientAuthenticationApi#requestEmailVerification(EmailRequestBody)
 */
class ClientEmailVerificationRequestTest extends AuthenticationEmailTestBase {

    private static final String EMAIL = "pending-client@example.test";

    /** 超过 60 秒重发冷却。 */
    private static final long COOLDOWN_ELAPSED_SECONDS = 61;

    /** 未验证账号重发：旧挑战 SUPERSEDED，新邮件链接与旧链接不同，均不落通知表。 */
    @Test
    void test_normalFlow() {
        loginEmailVault.allow(EMAIL);
        ses.accept(EMAIL, "ses-verification-1").accept(EMAIL, "ses-verification-2");
        anonymous(HttpMethod.POST, "/authentication/v1/client/register",
            new RegisterRequestBody(EMAIL, "Registration-Passphrase-2026")).assertStatus(202);
        long accountId = ((Number) operationsData.accounts(AuthenticationRealm.CUSTOMER.getCode()).getFirst()
            .get("account_id")).longValue();
        operationsData.backdateChallenges(accountId, COOLDOWN_ELAPSED_SECONDS);

        anonymous(HttpMethod.POST, "/authentication/v1/client/email-verification-requests",
            new EmailRequestBody(EMAIL)).assertStatus(202);

        var challenges = operationsData.challenges(accountId);
        var mails = ses.requests();
        assertAll(() -> assertEquals(2, challenges.size()),
            () -> assertEquals(ClientChallengeStatus.SUPERSEDED.getCode(), challenges.get(0).get("status")),
            () -> assertEquals(ClientChallengeStatus.PENDING.getCode(), challenges.get(1).get("status")),
            () -> assertEquals(2, mails.size()),
            () -> assertEquals(NotificationFixture.VERIFICATION_SUBJECT, mails.get(1).subject()),
            () -> assertTrue(mails.get(1).text().contains(PUBLIC_ORIGIN + "/verify-email#challengeNo=")),
            () -> assertNotEquals(link(mails.get(0))[1], link(mails.get(1))[1], "重发产生新秘密"));
        assertNothingPersisted();
    }

    /** 冷却期内重发返回同一受理结果，不签发新挑战，也不发信。 */
    @Test
    void test_withinCooldown() {
        loginEmailVault.allow(EMAIL);
        ses.accept(EMAIL, "ses-verification-1");
        anonymous(HttpMethod.POST, "/authentication/v1/client/register",
            new RegisterRequestBody(EMAIL, "Registration-Passphrase-2026")).assertStatus(202);

        anonymous(HttpMethod.POST, "/authentication/v1/client/email-verification-requests",
            new EmailRequestBody(EMAIL)).assertStatus(202);

        onlySesRequest();
    }
}
