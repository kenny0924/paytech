package com.paytech.risk.test.checker.evaluation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import com.paytech.risk.decision.domain.enums.VelocityEventCode;

/**
 * 累计事件行的 Checker：核对来源、金额、币种、事件主体，并按摘要合同独立重算事件摘要
 * <li>摘要合同 {@code CODE-MOR:RISK:VELOCITY_EVENT:1}：字段 amount（去尾零）、currency、eventCode、occurredTime、schema、sourceId、
 * sourceType 均为字符串，按 RFC 8785 排序键后取 SHA-256；测试按字段直接拼出规范 JSON，不调用生产摘要实现。</li>
 *
 * @author Kenny
 */
public final class VelocityEventChecker {
    /** 累计事件摘要合同。 */
    public static final String EVENT_SCHEMA = "CODE-MOR:RISK:VELOCITY_EVENT:1";

    /** 纯断言工具。 */
    private VelocityEventChecker() { }

    /**
     * 核对一条累计事件
     *
     * @param event 事件行
     * @param code 期望事件编码
     * @param sourceType 期望来源类型
     * @param sourceId 期望来源 ID
     * @param amount 期望金额
     * @param currency 期望币种
     */
    public static void assertEvent(Map<String, Object> event, VelocityEventCode code, String sourceType, long sourceId,
        BigDecimal amount, String currency) {
        Instant occurred = instant(event.get("occurred_time"));
        assertAll("累计事件 " + code.getCode(),
            () -> assertEquals(code.getCode(), event.get("event_code"), "事件编码"),
            () -> assertEquals(sourceType, event.get("source_type"), "来源类型"),
            () -> assertEquals(sourceId, ((Number)event.get("source_id")).longValue(), "来源 ID"),
            () -> assertEquals(1, ((Number)event.get("source_version")).intValue(), "来源版本"),
            () -> assertEquals(0, amount.compareTo((BigDecimal)event.get("amount")), "金额"),
            () -> assertEquals(currency, event.get("currency"), "币种"),
            () -> assertEquals(occurred, instant(event.get("create_time")), "创建时间等于发生时间"),
            () -> assertEquals(expectedHash(code, sourceType, sourceId, occurred, amount, currency),
                event.get("event_hash"), "事件摘要"));
    }

    /**
     * 核对事件主体：类型与键一一对应，事件编码与时间冻结自父事件
     *
     * @param event 父事件行
     * @param subjects 主体行，按主体类型排序
     * @param expected 期望的主体类型到主体键，按主体类型排序
     */
    public static void assertSubjects(Map<String, Object> event, List<Map<String, Object>> subjects,
        Map<String, String> expected) {
        assertEquals(List.copyOf(expected.keySet()), subjects.stream().map(row -> row.get("subject_type")).toList(),
            "主体类型");
        for (Map<String, Object> subject : subjects) {
            assertAll("事件主体 " + subject.get("subject_type"),
                () -> assertEquals(expected.get(subject.get("subject_type")), subject.get("subject_key"), "主体键"),
                () -> assertEquals(event.get("event_code"), subject.get("event_code"), "冻结事件编码"),
                () -> assertEquals(event.get("occurred_time"), subject.get("occurred_time"), "冻结发生时间"));
        }
    }

    /** 按摘要合同拼出规范 JSON 并取 SHA-256；各值只含不需转义的 ASCII 字符。 */
    static String expectedHash(VelocityEventCode code, String sourceType, long sourceId, Instant occurred,
        BigDecimal amount, String currency) {
        String canonical = "{\"amount\":\"" + amount.stripTrailingZeros().toPlainString() + "\",\"currency\":\"" + currency
            + "\",\"eventCode\":\"" + code.getCode() + "\",\"occurredTime\":\"" + occurred + "\",\"schema\":\""
            + EVENT_SCHEMA + "\",\"sourceId\":\"" + sourceId + "\",\"sourceType\":\"" + sourceType + "\"}";
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    /** 数据库 DATETIME(6) 按 UTC 读回。 */
    private static Instant instant(Object value) {
        if (value instanceof LocalDateTime time) {
            return time.toInstant(ZoneOffset.UTC);
        }
        return ((java.sql.Timestamp)value).toLocalDateTime().toInstant(ZoneOffset.UTC);
    }
}
