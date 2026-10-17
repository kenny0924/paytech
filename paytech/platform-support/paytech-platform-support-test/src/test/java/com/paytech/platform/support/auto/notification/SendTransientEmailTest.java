package com.paytech.platform.support.auto.notification;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderResult;
import com.paytech.platform.support.delivery.integration.provider.EmailProviderResult.Outcome;
import com.paytech.platform.support.delivery.integration.provider.ses.SesEmailProvider;
import com.paytech.platform.support.delivery.service.processor.SendTransientEmailProcessor;
import com.paytech.platform.support.delivery.service.processor.SubmitNotificationProcessor;
import com.paytech.platform.support.delivery.service.processor.request.SendTransientEmailRequest;
import com.paytech.platform.support.test.base.AuthenticationEmailTestBase;
import com.paytech.platform.support.test.builder.NotificationRequestBuilder;

/**
 * Delivery 即时发送模块入口：只接受即时模板，渲染后经真实 SES 适配器发送一次，不落库、不重试。
 * 普通通知入口不得使用即时模板。SES 结果码在生产代码中没有常量，按字面值断言。
 *
 * @see SendTransientEmailProcessor#process(Object)
 */
class SendTransientEmailTest extends AuthenticationEmailTestBase {

    static final String EMAIL = "transient@example.test";

    static final String VERIFICATION_TEMPLATE = "CLIENT_EMAIL_VERIFICATION";

    static final String LINK = "https://client.example.test/verify-email#challengeNo=x&secret=y";

    @Autowired SendTransientEmailProcessor processor;
    @Autowired SubmitNotificationProcessor submitProcessor;

    /** 受理：返回 SES 分类，主题与链接渲染正确，请求号以 EmailTag 发出，通知表与补偿任务无记录；未使用的多行参数允许。 */
    @Test
    void test_normalFlow() {
        ses.accept(EMAIL, "ses-transient-1");

        var response = processor.process(request(Map.of("link", LINK, "note", "line1\nline2")));

        var sent = onlySesRequest();
        assertAll(() -> assertEquals(Outcome.ACCEPTED, response.getOutcome()),
            () -> assertEquals("SES_ACCEPTED", response.getCode()),
            () -> assertEquals(EMAIL, sent.recipient()),
            () -> assertTrue(sent.text().contains(LINK)),
            () -> assertTrue(sent.body().path("EmailTags").get(0).path("Value").asString().startsWith("ses-")),
            () -> assertEquals(SesEmailProvider.REQUEST_NO_TAG,
                sent.body().path("EmailTags").get(0).path("Name").asString()));
        assertNothingPersisted();
    }

    /** SES 明确拒绝与断连只返回分类，不抛出、不重试。 */
    @ParameterizedTest(name = "{1}")
    @MethodSource("providerResults")
    void test_providerResult(Consumer<SendTransientEmailTest> reply, Outcome outcome, String code) {
        reply.accept(this);

        var response = processor.process(request(Map.of("link", LINK)));

        assertAll(() -> assertEquals(outcome, response.getOutcome()), () -> assertEquals(code, response.getCode()),
            () -> assertEquals(1, ses.requests().size()));
        assertNothingPersisted();
    }

    /** 节流可重试拒绝；断连结果未知。 */
    static Stream<Arguments> providerResults() {
        return Stream.of(
            Arguments.of((Consumer<SendTransientEmailTest>) test -> test.ses.fail(EMAIL, 429, "TooManyRequestsException",
                "Maximum sending rate exceeded."), Outcome.RETRYABLE_REJECTED, "SES_THROTTLED"),
            Arguments.of((Consumer<SendTransientEmailTest>) test -> test.ses.disconnect(EMAIL), Outcome.UNKNOWN,
                "SES_RESULT_UNKNOWN"));
    }

    /** 供应商结果缺分类、缺结果码或结果码不合法时按未知返回；合法的送达结果原样返回。 */
    @ParameterizedTest(name = "{1}")
    @MethodSource("providerEvidence")
    void test_invalidProviderEvidence(EmailProviderResult evidence, Outcome outcome, String code) {
        routeVerificationToMockProvider();
        provider.expectSendResult(EMAIL, evidence);

        var response = processor.process(request(Map.of("link", LINK)));

        assertAll(() -> assertEquals(outcome, response.getOutcome()), () -> assertEquals(code, response.getCode()),
            () -> assertEquals(1, provider.calls().size()), () -> assertTrue(ses.requests().isEmpty()));
    }

    /** 测试 Provider 才能构造的结果。 */
    static Stream<Arguments> providerEvidence() {
        return Stream.of(
            Arguments.of(new EmailProviderResult(null, null, "ACCEPTED"), Outcome.UNKNOWN, "RESULT_UNKNOWN"),
            Arguments.of(new EmailProviderResult(Outcome.ACCEPTED, null, null), Outcome.UNKNOWN, "RESULT_UNKNOWN"),
            Arguments.of(new EmailProviderResult(Outcome.ACCEPTED, null, "accepted"), Outcome.UNKNOWN, "RESULT_UNKNOWN"),
            Arguments.of(new EmailProviderResult(Outcome.DELIVERED, null, "DELIVERED"), Outcome.DELIVERED, "DELIVERED"));
    }

    /** 字段与临时参数非法：在调用 Vault 与 SES 前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    void test_invalidParameter(String scenario, Consumer<SendTransientEmailRequest> change) {
        var request = request(Map.of("link", LINK));
        change.accept(request);

        assertThrows(InvalidParameterException.class, () -> processor.process(request));

        assertAll(() -> assertEquals(0, vault.credentialRevealCount()), () -> assertTrue(ses.requests().isEmpty()));
    }

    /** 每组只引入一处非法值。 */
    static Stream<Arguments> invalidRequests() {
        Map<String, String> tooMany = new HashMap<>();
        IntStream.range(0, 9).forEach(i -> tooMany.put("p" + i, "v"));
        Map<String, String> nullValue = new HashMap<>();
        nullValue.put("link", null);
        Map<String, String> nullKey = new HashMap<>();
        nullKey.put(null, LINK);
        return Stream.of(
            Arguments.of("邮箱不合法", (Consumer<SendTransientEmailRequest>) r -> r.setEmail("not-an-email")),
            Arguments.of("语言不合法", (Consumer<SendTransientEmailRequest>) r -> r.setLocale("??")),
            Arguments.of("参数超过 8 个", (Consumer<SendTransientEmailRequest>) r -> r.setParameters(tooMany)),
            Arguments.of("参数值含控制字符", (Consumer<SendTransientEmailRequest>) r ->
                r.setParameters(Map.of("link", "a\u0000b"))),
            Arguments.of("参数值为空", (Consumer<SendTransientEmailRequest>) r -> r.setParameters(Map.of("link", ""))),
            Arguments.of("参数键不合法", (Consumer<SendTransientEmailRequest>) r ->
                r.setParameters(Map.of("1link", LINK))),
            Arguments.of("参数键为空", (Consumer<SendTransientEmailRequest>) r -> r.setParameters(nullKey)),
            Arguments.of("参数值为 null", (Consumer<SendTransientEmailRequest>) r -> r.setParameters(nullValue)),
            Arguments.of("参数值超过 2048", (Consumer<SendTransientEmailRequest>) r ->
                r.setParameters(Map.of("link", "x".repeat(2049)))));
    }

    /** 非即时模板、缺所需语言、缺模板参数：业务拒绝，不读取凭据、不发信。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedRequests")
    void test_businessRuleViolation(String scenario, Consumer<SendTransientEmailRequest> change, String message) {
        var request = request(Map.of("link", LINK));
        change.accept(request);

        var failure = assertThrows(BizCheckFailException.class, () -> processor.process(request));

        assertAll(() -> assertTrue(failure.getMessage().contains(message), failure.getMessage()),
            () -> assertEquals(0, vault.credentialRevealCount()), () -> assertTrue(ses.requests().isEmpty()));
    }

    /** 每组只引入一处业务不满足。 */
    static Stream<Arguments> rejectedRequests() {
        return Stream.of(
            Arguments.of("普通通知模板", (Consumer<SendTransientEmailRequest>) r ->
                r.setTemplateCode(NotificationRequestBuilder.TEMPLATE_CODE), "该模板不允许即时发送"),
            Arguments.of("缺少 en-US 内容", (Consumer<SendTransientEmailRequest>) r -> r.setLocale("en-US"),
                "邮件模板缺少所需语言"),
            Arguments.of("缺少 link 参数", (Consumer<SendTransientEmailRequest>) r ->
                r.setParameters(Map.of("other", LINK)), "邮件模板缺少必要参数"));
    }

    /** 普通通知提交即时模板被拒绝：不保护地址、不落库。 */
    @Test
    void test_ordinaryNotificationRejectsTransientTemplate() {
        var request = NotificationRequestBuilder.template();
        request.setTemplateCode(VERIFICATION_TEMPLATE);

        var failure = assertThrows(BizCheckFailException.class, () -> submitProcessor.process(request));

        assertAll(() -> assertTrue(failure.getMessage().contains("该模板只用于即时发送")),
            () -> assertEquals(0, vault.protectCount()));
        assertNothingPersisted();
    }

    private static SendTransientEmailRequest request(Map<String, String> parameters) {
        var request = new SendTransientEmailRequest();
        request.setTemplateCode(VERIFICATION_TEMPLATE);
        request.setEmail(EMAIL);
        request.setLocale("zh-CN");
        request.setParameters(parameters);
        return request;
    }
}
