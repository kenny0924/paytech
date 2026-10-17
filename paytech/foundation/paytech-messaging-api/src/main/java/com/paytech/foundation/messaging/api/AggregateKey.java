package com.paytech.foundation.messaging.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 事件所属聚合的身份：{@code aggregateType + aggregateId}，并负责补偿事件 {@code event_key} 的生成与解析。
 * <li>{@code eventKey = <aggregateType>:<aggregateId>:<eventType>:<aggregateVersion>}，最长 126 字符，适配
 * {@code t_compensation_event.event_key VARCHAR(128)}。</li>
 * <li>{@code aggregateId} 同时是 RabbitMQ 路由键（分区键），因此不得包含 {@code :}。</li>
 *
 * @author Claude
 */
public final class AggregateKey {

    /** 聚合类型：大写 Code，最长 32。 */
    public static final Pattern AGGREGATE_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,31}");

    /** 聚合标识：十进制 ID 或安全 ASCII 文本，最长 40。 */
    public static final Pattern AGGREGATE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,39}");

    /** 事件类型：PascalCase，最长 32。 */
    public static final Pattern EVENT_TYPE = Pattern.compile("[A-Z][A-Za-z0-9]{0,31}");

    private static final char SEPARATOR = ':';

    private final String aggregateType;
    private final String aggregateId;

    private AggregateKey(String aggregateType, String aggregateId) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
    }

    /**
     * 构造并校验聚合身份。
     *
     * @param aggregateType 聚合类型，例如 {@code LEDGER_BOOK}
     * @param aggregateId 聚合标识，例如账簿 ID 的十进制文本
     * @return 不可变聚合身份
     * @throws IllegalArgumentException 任一字段格式非法
     */
    public static AggregateKey of(String aggregateType, String aggregateId) {
        require(aggregateType, AGGREGATE_TYPE, "aggregateType");
        require(aggregateId, AGGREGATE_ID, "aggregateId");
        return new AggregateKey(aggregateType, aggregateId);
    }

    /**
     * 解析补偿事件的 {@code event_key}，取回其聚合身份，供补偿 Dispatcher 按聚合串行。
     *
     * @param eventKey 由 {@link #eventKey(String, long)} 生成的键
     * @return 键中的聚合身份
     * @throws IllegalArgumentException 键不符合四段格式或任一段非法
     */
    public static AggregateKey fromEventKey(String eventKey) {
        return parseEventKey(eventKey).aggregateKey();
    }

    /**
     * 完整解析补偿事件的 {@code event_key}。
     *
     * @param eventKey 由 {@link #eventKey(String, long)} 生成的键
     * @return 聚合身份、事件类型与聚合版本
     * @throws IllegalArgumentException 键不符合四段格式或任一段非法
     */
    public static ParsedEventKey parseEventKey(String eventKey) {
        if (eventKey == null) {
            throw new IllegalArgumentException("eventKey must not be null");
        }
        String[] parts = eventKey.split(String.valueOf(SEPARATOR), -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("eventKey must have four ':' separated parts");
        }
        AggregateKey key = of(parts[0], parts[1]);
        require(parts[2], EVENT_TYPE, "eventType");
        return new ParsedEventKey(key, parts[2], parseVersion(parts[3]));
    }

    /**
     * 生成补偿事件 {@code event_key}；同一聚合同一版本同一事件类型恒得同一键，可作为发布任务的业务唯一身份。
     *
     * @param eventType 事件类型
     * @param aggregateVersion 聚合版本，非负
     * @return 四段式键
     * @throws IllegalArgumentException 事件类型或版本非法
     */
    public String eventKey(String eventType, long aggregateVersion) {
        require(eventType, EVENT_TYPE, "eventType");
        requireVersion(aggregateVersion);
        return aggregateType + SEPARATOR + aggregateId + SEPARATOR + eventType + SEPARATOR + aggregateVersion;
    }

    /**
     * 聚合类型。
     *
     * @return 大写 Code
     */
    public String aggregateType() {
        return aggregateType;
    }

    /**
     * 聚合标识，也是消息路由键。
     *
     * @return 聚合标识
     */
    public String aggregateId() {
        return aggregateId;
    }

    /**
     * 校验聚合版本非负。
     *
     * @param aggregateVersion 版本
     */
    static void requireVersion(long aggregateVersion) {
        if (aggregateVersion < 0) {
            throw new IllegalArgumentException("aggregateVersion must not be negative");
        }
    }

    /**
     * 按格式校验必填文本。
     *
     * @param value 待校验值
     * @param pattern 格式
     * @param name 安全的字段名
     */
    static void require(String value, Pattern pattern, String name) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " has an invalid format");
        }
    }

    private static long parseVersion(String text) {
        if (text.isEmpty() || text.length() > 19 || !text.chars().allMatch(c -> c >= '0' && c <= '9')
            || (text.length() > 1 && text.charAt(0) == '0')) {
            throw new IllegalArgumentException("aggregateVersion has an invalid format");
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("aggregateVersion has an invalid format", exception);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AggregateKey that && aggregateType.equals(that.aggregateType)
            && aggregateId.equals(that.aggregateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aggregateType, aggregateId);
    }

    @Override
    public String toString() {
        return aggregateType + SEPARATOR + aggregateId;
    }

    /**
     * 解析后的 {@code event_key}。
     *
     * @param aggregateKey 聚合身份
     * @param eventType 事件类型
     * @param aggregateVersion 聚合版本
     */
    public record ParsedEventKey(AggregateKey aggregateKey, String eventType, long aggregateVersion) {
    }
}
