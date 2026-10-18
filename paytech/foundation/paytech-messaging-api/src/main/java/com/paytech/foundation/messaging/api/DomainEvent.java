package com.paytech.foundation.messaging.api;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 跨服务领域事件信封 Schema v1（design/08 §2），不可变。
 * <li>{@code aggregateId} 是分区键；{@code aggregateVersion} 单调递增，消费者据此判乱序与重放（D24）。</li>
 * <li>{@code payloadHash = sha256:<hex>}（Payload 的 RFC 8785 规范 bytes 的 SHA-256）不在本模块计算：发布端在 publish 时计算并写入信封，
 * 消费端解码时重新计算并校验；{@code -core} 构造的信封可不带哈希。本模块因此不依赖 {@code paytech-json-canonicalization}。</li>
 * <li>Payload 必须是 JSON Object，且符合 Foundation JSON Object Profile：金额、小数、64 位 ID 与时间一律为字符串（发布端校验）。</li>
 * <li>{@code eventId} 未指定时取 {@link AggregateKey#eventKey(String, long)}，保证同一业务事件在补偿重试中 ID 不变。</li>
 *
 * @author Claude
 */
public final class DomainEvent {

    /** 发布与消费服务名：01 §3 的英文服务名。 */
    public static final Pattern SERVICE_NAME = Pattern.compile("[a-z][a-z0-9-]{0,63}");

    /** 实例 ID。 */
    public static final Pattern INSTANCE_ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    /** 事件 ID：最长 128 的安全 ASCII 文本。 */
    public static final Pattern EVENT_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9:._-]{0,127}");

    /** 关联 ID：与 Request ID Profile 一致。 */
    public static final Pattern CORRELATION_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    /** Payload 哈希格式。 */
    public static final Pattern PAYLOAD_HASH = Pattern.compile("sha256:[0-9a-f]{64}");

    private final String eventId;
    private final String eventType;
    private final int schemaVersion;
    private final AggregateKey aggregateKey;
    private final long aggregateVersion;
    private final Instant occurredAt;
    private final String sourceService;
    private final String instanceId;
    private final String correlationId;
    private final String payloadHash;
    private final ObjectNode payload;

    private DomainEvent(Builder builder, ObjectNode payload) {
        this.eventType = builder.eventType;
        this.schemaVersion = builder.schemaVersion;
        this.aggregateKey = builder.aggregateKey;
        this.aggregateVersion = builder.aggregateVersion;
        this.eventId = builder.eventId != null ? builder.eventId
            : builder.aggregateKey.eventKey(builder.eventType, builder.aggregateVersion);
        this.occurredAt = builder.occurredAt;
        this.sourceService = builder.sourceService;
        this.instanceId = builder.instanceId;
        this.correlationId = builder.correlationId;
        this.payload = payload;
        this.payloadHash = builder.payloadHash;
    }

    /**
     * 创建信封构造器。
     *
     * @return 新构造器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 事件 ID，作为 AMQP {@code messageId} 与消费去重键。
     *
     * @return 事件 ID
     */
    public String eventId() {
        return eventId;
    }

    /**
     * 事件类型。
     *
     * @return PascalCase 事件类型
     */
    public String eventType() {
        return eventType;
    }

    /**
     * Payload Schema 版本。
     *
     * @return 正整数
     */
    public int schemaVersion() {
        return schemaVersion;
    }

    /**
     * 聚合身份。
     *
     * @return 聚合身份
     */
    public AggregateKey aggregateKey() {
        return aggregateKey;
    }

    /**
     * 聚合类型。
     *
     * @return 大写 Code
     */
    public String aggregateType() {
        return aggregateKey.aggregateType();
    }

    /**
     * 聚合标识，也是路由键。
     *
     * @return 聚合标识
     */
    public String aggregateId() {
        return aggregateKey.aggregateId();
    }

    /**
     * 聚合版本。
     *
     * @return 非负版本
     */
    public long aggregateVersion() {
        return aggregateVersion;
    }

    /**
     * 业务事实发生时间（UTC）。
     *
     * @return 发生时间
     */
    public Instant occurredAt() {
        return occurredAt;
    }

    /**
     * 发布服务名。
     *
     * @return 01 §3 服务名
     */
    public String sourceService() {
        return sourceService;
    }

    /**
     * 实例 ID。
     *
     * @return 实例 ID
     */
    public String instanceId() {
        return instanceId;
    }

    /**
     * 关联 ID，一般为触发请求的 Request ID。
     *
     * @return 关联 ID，可为 null
     */
    public String correlationId() {
        return correlationId;
    }

    /**
     * Payload 规范哈希。
     *
     * @return {@code sha256:<hex>}；{@code -core} 构造、尚未经发布端处理的信封为 null
     */
    public String payloadHash() {
        return payloadHash;
    }

    /**
     * Payload 的独立副本；修改返回值不影响信封。
     *
     * @return JSON Object 副本
     */
    public ObjectNode payload() {
        return payload.deepCopy();
    }

    /**
     * 生成本事件对应的补偿事件 {@code event_key}。
     *
     * @return 四段式键
     */
    public String eventKey() {
        return aggregateKey.eventKey(eventType, aggregateVersion);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DomainEvent that && eventId.equals(that.eventId) && eventType.equals(that.eventType)
            && schemaVersion == that.schemaVersion && aggregateKey.equals(that.aggregateKey)
            && aggregateVersion == that.aggregateVersion && occurredAt.equals(that.occurredAt)
            && sourceService.equals(that.sourceService) && instanceId.equals(that.instanceId)
            && Objects.equals(correlationId, that.correlationId) && Objects.equals(payloadHash, that.payloadHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, aggregateVersion);
    }

    @Override
    public String toString() {
        return "DomainEvent[" + eventType + " " + aggregateKey + " v" + aggregateVersion + " id=" + eventId + "]";
    }

    /**
     * 信封构造器；{@link #build()} 统一校验全部字段并计算 Payload 哈希。
     */
    public static final class Builder {

        private String eventId;
        private String eventType;
        private int schemaVersion = 1;
        private AggregateKey aggregateKey;
        private long aggregateVersion = -1;
        private Instant occurredAt;
        private String sourceService;
        private String instanceId;
        private String correlationId;
        private JsonNode payload;
        private String payloadHash;

        private Builder() {
        }

        /**
         * 显式事件 ID；省略时使用 {@code eventKey}。
         *
         * @param eventId 事件 ID
         * @return 本构造器
         */
        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        /**
         * 事件类型。
         *
         * @param eventType PascalCase 事件类型
         * @return 本构造器
         */
        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        /**
         * Payload Schema 版本，默认 1。
         *
         * @param schemaVersion 正整数
         * @return 本构造器
         */
        public Builder schemaVersion(int schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        /**
         * 聚合身份。
         *
         * @param aggregateKey 聚合身份
         * @return 本构造器
         */
        public Builder aggregate(AggregateKey aggregateKey) {
            this.aggregateKey = aggregateKey;
            return this;
        }

        /**
         * 聚合身份的便捷写法。
         *
         * @param aggregateType 聚合类型
         * @param aggregateId 聚合标识
         * @return 本构造器
         */
        public Builder aggregate(String aggregateType, String aggregateId) {
            return aggregate(AggregateKey.of(aggregateType, aggregateId));
        }

        /**
         * 聚合版本。
         *
         * @param aggregateVersion 非负版本
         * @return 本构造器
         */
        public Builder aggregateVersion(long aggregateVersion) {
            this.aggregateVersion = aggregateVersion;
            return this;
        }

        /**
         * 业务事实发生时间。
         *
         * @param occurredAt UTC 时间
         * @return 本构造器
         */
        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        /**
         * 发布服务名。
         *
         * @param sourceService 01 §3 服务名
         * @return 本构造器
         */
        public Builder sourceService(String sourceService) {
            this.sourceService = sourceService;
            return this;
        }

        /**
         * 实例 ID。
         *
         * @param instanceId 实例 ID
         * @return 本构造器
         */
        public Builder instanceId(String instanceId) {
            this.instanceId = instanceId;
            return this;
        }

        /**
         * 关联 ID，可省略。
         *
         * @param correlationId Request ID 格式的关联 ID
         * @return 本构造器
         */
        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        /**
         * Payload；构造时复制，之后修改入参不影响信封。
         *
         * @param payload JSON Object
         * @return 本构造器
         */
        public Builder payload(JsonNode payload) {
            this.payload = payload;
            return this;
        }

        /**
         * Payload 规范哈希；由发布端或消费端解码器填入，{@code -core} 通常省略。
         *
         * @param payloadHash {@code sha256:<64 位小写十六进制>}
         * @return 本构造器
         */
        public Builder payloadHash(String payloadHash) {
            this.payloadHash = payloadHash;
            return this;
        }

        /**
         * 校验全部字段并复制 Payload。
         *
         * @return 不可变信封
         * @throws IllegalArgumentException 任一字段缺失或格式非法
         */
        public DomainEvent build() {
            AggregateKey.require(eventType, AggregateKey.EVENT_TYPE, "eventType");
            if (schemaVersion < 1) {
                throw new IllegalArgumentException("schemaVersion must be positive");
            }
            if (aggregateKey == null) {
                throw new IllegalArgumentException("aggregate must not be null");
            }
            AggregateKey.requireVersion(aggregateVersion);
            if (occurredAt == null) {
                throw new IllegalArgumentException("occurredAt must not be null");
            }
            AggregateKey.require(sourceService, SERVICE_NAME, "sourceService");
            AggregateKey.require(instanceId, INSTANCE_ID, "instanceId");
            if (eventId != null) {
                AggregateKey.require(eventId, EVENT_ID, "eventId");
            }
            if (correlationId != null) {
                AggregateKey.require(correlationId, CORRELATION_ID, "correlationId");
            }
            if (payloadHash != null) {
                AggregateKey.require(payloadHash, PAYLOAD_HASH, "payloadHash");
            }
            if (!(payload instanceof ObjectNode objectPayload)) {
                throw new IllegalArgumentException("payload must be a JSON object");
            }
            ObjectNode copy = objectPayload.deepCopy();
            return new DomainEvent(this, copy);
        }
    }
}
