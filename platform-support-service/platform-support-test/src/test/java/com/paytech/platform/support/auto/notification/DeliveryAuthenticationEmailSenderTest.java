package com.paytech.platform.support.auto.notification;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.accesscontrol.domain.authentication.ClientEmailChallengePolicy.LinkMaterial;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengePurpose;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderResult.Outcome;
import com.paytech.platform.support.delivery.service.processor.SendTransientEmailProcessor;
import com.paytech.platform.support.integration.mail.DeliveryAuthenticationEmailSender;
import com.paytech.platform.support.test.base.AuthenticationEmailTestBase;

/**
 * 认证邮件端口适配器：站点 Origin 启动校验，以及供应商结果到受理/失败指标的映射。
 * 认证入口的正常与失败链路见 ClientRegistrationTest 等 HTTP 用例；这里覆盖 SES 不会返回的送达类结果。
 *
 * @see DeliveryAuthenticationEmailSender#send(String, ClientChallengePurpose, LinkMaterial)
 */
class DeliveryAuthenticationEmailSenderTest extends AuthenticationEmailTestBase {

    static final String EMAIL = "adapter@example.test";

    static final LinkMaterial LINK = new LinkMaterial("00000000-0000-0000-0000-000000000001",
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ");

    @Autowired SendTransientEmailProcessor processor;

    /** 非 HTTPS（本机除外）、带用户信息/查询/片段/路径或缺主机的 Origin 拒绝构造。 */
    @ParameterizedTest
    @ValueSource(strings = {"http://client.example.test", "https://user@client.example.test",
        "https://client.example.test?x=1", "https://client.example.test#f", "https://client.example.test/app",
        "/relative", "http://example.com:8080"})
    void test_invalidOrigin(String origin) {
        assertThrows(IllegalArgumentException.class, () -> new DeliveryAuthenticationEmailSender(processor, origin, meters));
    }

    /** HTTPS、本机 http 与末尾斜杠均可接受。 */
    @ParameterizedTest
    @ValueSource(strings = {"https://client.example.test/", "http://localhost:3000", "http://127.0.0.1", "http://[::1]:8080"})
    void test_validOrigin(String origin) {
        assertDoesNotThrow(() -> new DeliveryAuthenticationEmailSender(processor, origin, meters));
    }

    /** 送达视为成功；无法送达与可重试拒绝计 REJECTED 并抛出不可用，链接去掉末尾斜杠后拼接。 */
    @ParameterizedTest(name = "{0}")
    @CsvSource({"DELIVERED,", "UNDELIVERABLE,REJECTED", "RETRYABLE_REJECTED,REJECTED"})
    void test_providerOutcome(Outcome outcome, String reason) {
        routeVerificationToMockProvider();
        provider.expectSend(EMAIL, outcome);
        var sender = new DeliveryAuthenticationEmailSender(processor, PUBLIC_ORIGIN + "/", meters);
        double acceptedBefore = accepted();
        double failedBefore = reason == null ? 0 : failed(reason);

        if (reason == null) {
            sender.send(EMAIL, ClientChallengePurpose.SIGN_UP_EMAIL, LINK);
        } else {
            assertThrows(ServiceUnavailableException.class,
                () -> sender.send(EMAIL, ClientChallengePurpose.SIGN_UP_EMAIL, LINK));
        }

        var body = provider.calls().getFirst().getBody();
        assertAll(() -> assertTrue(body.contains(PUBLIC_ORIGIN + "/verify-email#challengeNo=" + LINK.challengeNo()
                + "&secret=" + LINK.secret())),
            () -> assertEquals(reason == null ? 1 : 0, accepted() - acceptedBefore),
            () -> assertEquals(reason == null ? 0 : 1, reason == null ? 0 : failed(reason) - failedBefore));
    }

    /** 参数不合法（非法邮箱）计 CONFIGURATION，不调用 Provider。 */
    @Test
    void test_invalidParameter() {
        var sender = new DeliveryAuthenticationEmailSender(processor, PUBLIC_ORIGIN, meters);
        double failedBefore = failed("CONFIGURATION");

        assertThrows(ServiceUnavailableException.class,
            () -> sender.send("not-an-email", ClientChallengePurpose.PASSWORD_RESET, LINK));

        assertAll(() -> assertEquals(1, failed("CONFIGURATION") - failedBefore),
            () -> assertTrue(provider.calls().isEmpty()), () -> assertTrue(ses.requests().isEmpty()));
    }
}
