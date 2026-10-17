package com.paytech.acquiring.test.checker.webhook;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.acquiring.domain.enums.ClientWebhookEventType;
import com.paytech.acquiring.ext.job.ClientWebhookSubmissionHandler;
import com.paytech.acquiring.test.dal.ClientWebhookTestMapper;
import com.paytech.acquiring.test.mocker.external.PlatformWebhookMocker;
import com.paytech.acquiring.test.mocker.external.PlatformWebhookMocker.Submission;
import com.paytech.foundation.jsoncanonicalization.api.Rfc8785JsonCanonicalizer;

import lombok.AllArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 观察 Platform 收到的冻结事件与本地补偿任务；只读断言，不推进任务。 */
@TestComponent
@AllArgsConstructor
public class ClientWebhookChecker {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> ENVELOPE_KEYS = Set.of("eventId", "eventType", "schemaVersion", "createdAt", "data");

    private final PlatformWebhookMocker webhooks;
    private final ClientWebhookTestMapper mapper;

    /** 等待事件累计提交到指定次数并返回最后一次。 */
    public Submission awaitSubmissions(String eventId, int count) {
        await().atMost(Duration.ofSeconds(10)).until(() -> webhooks.submissions(eventId).size() >= count);
        var submissions = webhooks.submissions(eventId);
        assertEquals(count, submissions.size(), "Platform 收到的提交次数: " + eventId);
        return submissions.getLast();
    }

    /** 当前已收到的提交，不等待。 */
    public List<Submission> submissions(String eventId) {
        return webhooks.submissions(eventId);
    }

    /** 等待 Platform 受理后任务被删除。 */
    public void awaitTaskCompleted(String eventId) {
        await().atMost(Duration.ofSeconds(10)).until(() -> mapper.task(eventId) == null);
    }

    /** 等待任务进入指定执行状态并返回该行。 */
    public Map<String, Object> awaitTask(String eventId, String status) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var task = mapper.task(eventId);
            assertTrue(task != null && status.equals(task.get("execute_status")) && task.get("lock_owner") == null,
                "Webhook 任务状态: " + task);
        });
        return mapper.task(eventId);
    }

    /** 没有为该事件登记任务。 */
    public void assertNoTask(String eventId) {
        assertNull(mapper.task(eventId), "不应登记 Webhook 任务: " + eventId);
    }

    /**
     * 核对提交 Header、请求字段与 Envelope 结构，并以独立规范化复算两个 Hash。
     *
     * @param expectedObject 期望的 data.object，与实际按 RFC 8785 规范字节比较
     */
    public void assertFrozen(Submission submission, ClientWebhookEventType type, String eventId, long clientId,
        String createdAt, JsonNode expectedObject) {
        var envelope = submission.envelope();
        var object = envelope.path("data").path("object");
        assertAll(() -> assertEquals(ClientWebhookSubmissionHandler.CALLER_SERVICE, submission.callerService()),
            () -> assertEquals(Long.toString(clientId), submission.clientId()),
            () -> assertEquals(ENVELOPE_KEYS, keys(envelope)), () -> assertEquals(Set.of("object"), keys(envelope.path("data"))),
            () -> assertEquals(eventId, envelope.path("eventId").asString()),
            () -> assertEquals(type.getCode(), envelope.path("eventType").asString()),
            () -> assertEquals("1", envelope.path("schemaVersion").asString()),
            () -> assertEquals(createdAt, envelope.path("createdAt").asString()),
            () -> assertEquals(object.path("version").longValue(), submission.sourceVersion()),
            () -> assertEquals(sha256(canonical(envelope)), submission.payloadHash()),
            () -> assertEquals(sha256(canonical(object)), submission.sourcePayloadHash()),
            () -> assertTrue(canonical(envelope).length <= ClientWebhookSubmissionHandler.MAX_BYTES),
            () -> assertEquals(canonicalText(expectedObject), canonicalText(object), "data.object"));
    }

    /** 两次提交内容完全相同（冻结内容重放）。 */
    public void assertSameContent(Submission first, Submission second) {
        assertAll(() -> assertEquals(canonicalText(first.envelope()), canonicalText(second.envelope())),
            () -> assertEquals(first.payloadHash(), second.payloadHash()),
            () -> assertEquals(first.sourcePayloadHash(), second.sourcePayloadHash()),
            () -> assertEquals(first.sourceVersion(), second.sourceVersion()),
            () -> assertEquals(first.clientId(), second.clientId()));
    }

    /** 任务行的错误代码与次数。 */
    public void assertTask(Map<String, Object> task, String status, int executeCount, String errorCode) {
        assertNotNull(task, "Webhook 任务不存在");
        assertAll(() -> assertEquals(status, task.get("execute_status")),
            () -> assertEquals(executeCount, ((Number)task.get("execute_count")).intValue()),
            () -> assertEquals(errorCode, task.get("last_error_code")));
    }

    /** RFC 8785 规范文本，与键顺序和整数节点类型无关。 */
    public static String canonicalText(JsonNode node) {
        return new String(canonical(node), StandardCharsets.UTF_8);
    }

    /** 测试侧独立规范化。 */
    static byte[] canonical(JsonNode node) {
        return Rfc8785JsonCanonicalizer.canonicalizeObject(JSON.writeValueAsBytes(node));
    }

    /** SHA-256 小写十六进制。 */
    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException missing) {
            throw new AssertionError(missing);
        }
    }

    /** 对象的全部属性名。 */
    static Set<String> keys(JsonNode node) {
        var keys = new HashSet<String>();
        node.propertyNames().forEach(keys::add);
        return keys;
    }
}
