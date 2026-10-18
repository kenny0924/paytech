package com.paytech.platform.support.auto.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.platform.support.delivery.core.enums.NotificationEventOutcome;
import com.paytech.platform.support.delivery.integration.provider.ses.SesEventParser;
import com.paytech.platform.support.delivery.integration.provider.ses.SesEventProperties;
import com.paytech.platform.support.test.builder.SesEventBuilder;

/**
 * SES 投递事件的纯 Java 合同：事件解析的白名单与失败返回空、结果优先级规则、拉取配置的启动校验。
 * 拉取、回写与 SQS 错误分类的完整链路见 NotificationEventPollTest。
 *
 * @see SesEventParser#parse(String)
 * @see NotificationEventOutcome#overrides(NotificationEventOutcome)
 */
class SesEventContractTest {

    /** 送达：结果、消息标识、尝试身份与时间取自白名单字段。 */
    @Test
    void test_parseDelivery() {
        var event = SesEventParser.parse(SesEventBuilder.delivery(123L, "ses-1"));

        assertEquals("DELIVERY", event.getEventType());
        assertEquals(123L, event.getAttemptId());
        assertEquals("ses-1", event.getMessageId());
        assertEquals(NotificationEventOutcome.DELIVERED, event.getOutcome());
        assertNull(event.getOutcomeCode());
        assertEquals(Instant.parse(SesEventBuilder.EVENT_TIME), event.getOccurredTime());
    }

    /** SNS 通知格式使用 notificationType；缺少明细时间时取邮件时间；无子类型的退信只记类型；拒发无原因；投诉分类回退。 */
    @Test
    void test_parseVariants() {
        String mail = "\"mail\":{\"messageId\":\"ses-1\",\"timestamp\":\"2026-10-17T08:00:00Z\","
            + "\"tags\":{\"notification_request_no\":[\"ses-1\"]}}";
        var legacy = SesEventParser.parse("{\"notificationType\":\"Delivery\"," + mail + ",\"delivery\":{}}");
        assertEquals(Instant.parse("2026-10-17T08:00:00Z"), legacy.getOccurredTime());

        var bounce = SesEventParser.parse("{\"eventType\":\"Bounce\"," + mail + ",\"bounce\":{\"bounceType\":\"Undetermined\"}}");
        assertEquals(NotificationEventOutcome.SOFT_BOUNCE, bounce.getOutcome());
        assertEquals("Undetermined", bounce.getOutcomeCode());

        var reject = SesEventParser.parse("{\"eventType\":\"Reject\"," + mail + "}");
        assertEquals("Reject", reject.getOutcomeCode());

        var suppressed = SesEventParser.parse("{\"eventType\":\"Complaint\"," + mail
            + ",\"complaint\":{\"complaintSubType\":\"OnAccountSuppressionList\"}}");
        assertEquals("OnAccountSuppressionList", suppressed.getComplaintCode());
        var unknown = SesEventParser.parse("{\"eventType\":\"Complaint\"," + mail + ",\"complaint\":{}}");
        assertEquals("unknown", unknown.getComplaintCode());
        assertTrue(unknown.isComplaint());
    }

    /** 分类码只保留安全字符并限制 64 字符。 */
    @Test
    void test_codeSanitized() {
        var event = SesEventParser.parse(SesEventBuilder.reject(1L, "ses-1", "<b>" + "x".repeat(100)));
        assertEquals(64, event.getOutcomeCode().length());
        assertTrue(event.getOutcomeCode().startsWith("Reject/bxxx"), "尖括号被去除");
    }

    /** 标签缺失、不是本平台格式、超出 long 范围或类型不对时尝试身份为空（匹配不到）。 */
    @ParameterizedTest
    @MethodSource("foreignTags")
    void test_foreignTagHasNoAttempt(String tagValue) {
        String body = SesEventBuilder.delivery(7L, "ses-1").replace("\"ses-7\"", tagValue);
        assertNull(SesEventParser.parse(body).getAttemptId());
    }

    /** 外部系统或其他格式的请求号标签。 */
    static Stream<String> foreignTags() {
        return Stream.of("\"web-7\"", "\"ses-0\"", "\"ses-99999999999999999999\"", "7");
    }

    /** 不合法的消息返回空，由调用方保留在队列中。 */
    @ParameterizedTest
    @MethodSource("invalidBodies")
    void test_invalidReturnsNull(String body) {
        assertNull(SesEventParser.parse(body));
    }

    /** 空、非 JSON、非对象、无类型、缺少消息标识、时间不合法、退信无类型、投诉无明细、信封内不合法。 */
    static Stream<String> invalidBodies() {
        String tags = "\"tags\":{\"notification_request_no\":[\"ses-1\"]}";
        return Stream.of(null, " ", "not-json", "[1]", "{\"mail\":{}}",
            "{\"eventType\":\"Delivery\",\"mail\":{\"messageId\":\"\",\"timestamp\":\"2026-10-17T08:00:00Z\"}}",
            "{\"eventType\":\"Delivery\",\"mail\":{\"messageId\":\"ses-1\",\"timestamp\":\"later\"," + tags + "}}",
            "{\"eventType\":\"Bounce\",\"mail\":{\"messageId\":\"ses-1\",\"timestamp\":\"2026-10-17T08:00:00Z\"},\"bounce\":{}}",
            "{\"eventType\":\"Complaint\",\"mail\":{\"messageId\":\"ses-1\"}}",
            "{\"Type\":\"Notification\",\"Message\":\"not-json\"}");
    }

    /** 其他事件类型解析为不处理。 */
    @Test
    void test_otherTypeIgnored() {
        var event = SesEventParser.parse(SesEventBuilder.other("Open", 1L, "ses-1"));
        assertEquals("OTHER", event.getEventType());
        assertTrue(event.isIgnored());
    }

    /** 优先级只升不降，同级保留先到；硬退信与拒发为不可投递。 */
    @Test
    void test_outcomePriority() {
        assertTrue(NotificationEventOutcome.DELIVERED.overrides(NotificationEventOutcome.SOFT_BOUNCE));
        assertTrue(NotificationEventOutcome.HARD_BOUNCE.overrides(NotificationEventOutcome.DELIVERED));
        assertTrue(!NotificationEventOutcome.REJECTED.overrides(NotificationEventOutcome.HARD_BOUNCE));
        assertTrue(!NotificationEventOutcome.SOFT_BOUNCE.overrides(NotificationEventOutcome.DELIVERED));
        assertTrue(NotificationEventOutcome.REJECTED.undeliverable() && !NotificationEventOutcome.DELIVERED.undeliverable());
    }

    /** 默认值合法；越界、读超时不大于长轮询等待、非法终端拒绝启动。 */
    @Test
    void test_propertiesValidation() {
        var defaults = new SesEventProperties(null, null, null, null, null, null, null);
        assertEquals(Duration.ofSeconds(60), defaults.getPollInterval());
        assertEquals(5, defaults.getMaxBatches());
        assertEquals(Duration.ofSeconds(15), defaults.getReadTimeout());

        assertThrows(IllegalArgumentException.class,
            () -> new SesEventProperties(Duration.ofSeconds(5), null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
            () -> new SesEventProperties(null, Duration.ofSeconds(21), null, null, null, null, Duration.ofSeconds(30)));
        assertThrows(IllegalArgumentException.class, () -> new SesEventProperties(null, null, 0, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new SesEventProperties(null, null, 21, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
            () -> new SesEventProperties(null, null, null, Duration.ofSeconds(10), null, null, null));
        assertThrows(IllegalArgumentException.class,
            () -> new SesEventProperties(null, Duration.ofSeconds(10), null, null, null, null, Duration.ofSeconds(10)));
        assertThrows(IllegalArgumentException.class,
            () -> new SesEventProperties(null, null, null, null, URI.create("http://sqs.example.com"), null, null));
    }
}
