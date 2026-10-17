package com.paytech.platform.support.test.builder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.paytech.platform.support.delivery.integration.provider.ses.SesEmailProvider;

import tools.jackson.databind.json.JsonMapper;

/**
 * 构造 SES 经 SNS 投递到 SQS 的事件消息体，字段形状与 SES 事件发布一致。
 * 每条事件都带收件地址与邮件头，用于验证平台不保存、不记录它们。
 */
public final class SesEventBuilder {

    /** 事件中的收件地址；不得出现在数据库、日志或指标中。 */
    public static final String RECIPIENT = "event-recipient@example.com";

    /** 事件中的邮件主题头；同上。 */
    public static final String SUBJECT_HEADER = "EVENT-SUBJECT-MARKER";

    /** 事件发生时间。 */
    public static final String EVENT_TIME = "2026-10-17T08:00:01.250Z";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SesEventBuilder() {
    }

    /** 送达。 */
    public static String delivery(Object attemptId, String messageId) {
        return event("Delivery", attemptId, messageId, "delivery",
            Map.of("timestamp", EVENT_TIME, "recipients", List.of(RECIPIENT), "smtpResponse", "250 2.6.0 ok"));
    }

    /** 退信；bounceType 为 Permanent、Transient 或 Undetermined。 */
    public static String bounce(Object attemptId, String messageId, String bounceType, String bounceSubType) {
        return event("Bounce", attemptId, messageId, "bounce", Map.of("bounceType", bounceType, "bounceSubType",
            bounceSubType, "timestamp", EVENT_TIME, "bouncedRecipients",
            List.of(Map.of("emailAddress", RECIPIENT, "diagnosticCode", "smtp; 550 " + RECIPIENT))));
    }

    /** 投诉。 */
    public static String complaint(Object attemptId, String messageId, String feedbackType) {
        return event("Complaint", attemptId, messageId, "complaint", Map.of("complaintFeedbackType", feedbackType,
            "timestamp", EVENT_TIME, "complainedRecipients", List.of(Map.of("emailAddress", RECIPIENT))));
    }

    /** 拒发。 */
    public static String reject(Object attemptId, String messageId, String reason) {
        return event("Reject", attemptId, messageId, "reject", Map.of("reason", reason));
    }

    /** 不处理的事件类型，如 Send、Open。 */
    public static String other(String eventType, Object attemptId, String messageId) {
        return event(eventType, attemptId, messageId, "send", Map.of());
    }

    /** 不带发送请求号标签的送达（来自其他系统或未打标签的邮件）。 */
    public static String deliveryWithoutTag(String messageId) {
        var mail = mail(null, messageId);
        return JSON.writeValueAsString(Map.of("eventType", "Delivery", "mail", mail,
            "delivery", Map.of("timestamp", EVENT_TIME)));
    }

    /** 未开启 Raw 投递时的 SNS 信封。 */
    public static String snsEnvelope(String sesEvent) {
        return JSON.writeValueAsString(Map.of("Type", "Notification", "MessageId", "sns-1",
            "TopicArn", "arn:aws:sns:ap-southeast-1:123456789012:ses-events", "Message", sesEvent));
    }

    /** 组装一条事件。 */
    static String event(String eventType, Object attemptId, String messageId, String detailName, Map<String, ?> detail) {
        var event = new LinkedHashMap<String, Object>();
        event.put("eventType", eventType);
        event.put("mail", mail(attemptId, messageId));
        event.put(detailName, detail);
        return JSON.writeValueAsString(event);
    }

    /** 邮件对象：标签含发送请求号 ses-<attempt_id>，另含收件地址与主题头。 */
    static Map<String, Object> mail(Object attemptId, String messageId) {
        var tags = new LinkedHashMap<String, Object>();
        tags.put("ses:configuration-set", List.of("notification-test"));
        if (attemptId != null) {
            tags.put(SesEmailProvider.REQUEST_NO_TAG, List.of("ses-" + attemptId));
        }
        var mail = new LinkedHashMap<String, Object>();
        mail.put("timestamp", "2026-10-17T08:00:00.000Z");
        mail.put("messageId", messageId);
        mail.put("source", "noreply@example.com");
        mail.put("destination", List.of(RECIPIENT));
        mail.put("commonHeaders", Map.of("subject", SUBJECT_HEADER, "to", List.of(RECIPIENT)));
        mail.put("tags", tags);
        return mail;
    }
}
