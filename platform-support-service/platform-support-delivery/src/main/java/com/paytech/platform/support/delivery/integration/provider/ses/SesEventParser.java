package com.paytech.platform.support.delivery.integration.provider.ses;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.paytech.platform.support.delivery.core.enums.NotificationEventOutcome;
import com.paytech.platform.support.delivery.domain.NotificationProviderEvent;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 解析 SES 经 SNS 投递到 SQS 的事件消息，只读取白名单字段
 * <li>同时接受 Raw 投递（消息体即 SES 事件）与 SNS 信封（Type=Notification 时取 Message）</li>
 * <li>收件地址、邮件头、诊断原文不读取；解析失败返回空，调用方保留消息由死信队列兜底</li>
 */
public final class SesEventParser {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 发送请求号标签值：由 SesEmailProvider.requestNo 生成的 ses-<attempt_id>。 */
    private static final Pattern REQUEST_NO = Pattern.compile("ses-([1-9][0-9]{0,18})");

    /** 分类码只保留的字符。 */
    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9/_ -]");

    private static final int CODE_LENGTH = 64;

    private SesEventParser() {
    }

    /**
     * 解析一条队列消息
     *
     * @param body 队列消息体
     * @return 事件白名单字段；JSON 不合法、缺少必需字段或时间格式错误时返回空
     */
    public static NotificationProviderEvent parse(String body) {
        JsonNode event = unwrap(read(body));
        if (event == null) {
            return null;
        }
        String type = text(event, "eventType");
        if (type == null) {
            type = text(event, "notificationType");
        }
        if (type == null) {
            return null;
        }
        return switch (type) {
            case "Delivery" -> delivery(event);
            case "Bounce" -> bounce(event);
            case "Complaint" -> complaint(event);
            case "Reject" -> reject(event);
            default -> new NotificationProviderEvent("OTHER", null, null, null, null, null, null);
        };
    }

    /** 读取 JSON 对象，不合法返回空；异常可能回显消息片段，不保留。 */
    static JsonNode read(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = JSON.readTree(body);
            return node != null && node.isObject() ? node : null;
        } catch (JacksonException malformed) {
            return null;
        }
    }

    /** SNS 信封取其中的 Message，Raw 投递原样返回。 */
    static JsonNode unwrap(JsonNode node) {
        if (node == null || !"Notification".equals(text(node, "Type"))) {
            return node;
        }
        return read(text(node, "Message"));
    }

    /** 送达：时间取 delivery.timestamp。 */
    static NotificationProviderEvent delivery(JsonNode event) {
        return outcome(event, "DELIVERY", NotificationEventOutcome.DELIVERED, null, event.get("delivery"));
    }

    /** 退信：Permanent 为硬退信，其他（Transient、Undetermined）为软退信；分类码为 bounceType/bounceSubType。 */
    static NotificationProviderEvent bounce(JsonNode event) {
        JsonNode bounce = event.get("bounce");
        String bounceType = text(bounce, "bounceType");
        if (bounceType == null) {
            return null;
        }
        var outcome = "Permanent".equals(bounceType) ? NotificationEventOutcome.HARD_BOUNCE
            : NotificationEventOutcome.SOFT_BOUNCE;
        String subType = text(bounce, "bounceSubType");
        return outcome(event, "BOUNCE", outcome, safeCode(subType == null ? bounceType : bounceType + "/" + subType),
            bounce);
    }

    /** 拒发：分类码为 Reject/<reason>，时间取邮件时间。 */
    static NotificationProviderEvent reject(JsonNode event) {
        String reason = text(event.get("reject"), "reason");
        return outcome(event, "REJECT", NotificationEventOutcome.REJECTED,
            safeCode(reason == null ? "Reject" : "Reject/" + reason), null);
    }

    /** 投诉：分类码取 complaintFeedbackType，没有时取 complaintSubType，再没有为 unknown。 */
    static NotificationProviderEvent complaint(JsonNode event) {
        JsonNode complaint = event.get("complaint");
        JsonNode mail = event.get("mail");
        String messageId = text(mail, "messageId");
        Instant time = time(complaint, mail);
        if (complaint == null || messageId == null || time == null) {
            return null;
        }
        String code = text(complaint, "complaintFeedbackType");
        if (code == null) {
            code = text(complaint, "complaintSubType");
        }
        return new NotificationProviderEvent("COMPLAINT", attemptId(mail), messageId, null, null,
            safeCode(code == null ? "unknown" : code), time);
    }

    /** 带投递结果的事件；缺少消息标识或时间时返回空。 */
    static NotificationProviderEvent outcome(JsonNode event, String type, NotificationEventOutcome outcome, String code,
        JsonNode detail) {
        JsonNode mail = event.get("mail");
        String messageId = text(mail, "messageId");
        Instant time = time(detail, mail);
        if (messageId == null || time == null) {
            return null;
        }
        return new NotificationProviderEvent(type, attemptId(mail), messageId, outcome, code, null, time);
    }

    /** 从 mail.tags 的发送请求号标签解析尝试身份；不是本平台尝试时为空。 */
    static Long attemptId(JsonNode mail) {
        JsonNode tags = mail == null ? null : mail.get("tags");
        JsonNode values = tags == null ? null : tags.get(SesEmailProvider.REQUEST_NO_TAG);
        if (values == null || !values.isArray() || values.isEmpty() || !values.get(0).isString()) {
            return null;
        }
        Matcher matcher = REQUEST_NO.matcher(values.get(0).asString());
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException outOfRange) {
            return null;
        }
    }

    /** 事件明细时间优先，缺失时用邮件时间；格式错误返回空。 */
    static Instant time(JsonNode detail, JsonNode mail) {
        String value = text(detail, "timestamp");
        if (value == null) {
            value = text(mail, "timestamp");
        }
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException malformed) {
            return null;
        }
    }

    /** 只保留安全字符并限制长度。 */
    static String safeCode(String value) {
        String safe = UNSAFE.matcher(value).replaceAll("");
        return safe.length() > CODE_LENGTH ? safe.substring(0, CODE_LENGTH) : safe;
    }

    /** 读取非空字符串字段，缺失返回空。 */
    static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            return null;
        }
        return value.asString();
    }
}
