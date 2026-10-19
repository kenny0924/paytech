package com.paytech.account.api.event;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import com.paytech.account.api.enums.AccountEntityType;

import lombok.Getter;
import lombok.Setter;

/**
 * AccountPosted Schema v1 Payload（事件目录 catalog/events.yaml，payloadSchemaRef paytech-account-api/event/AccountPosted/v1）：
 * account 每生成一个过账批次（t_account_posting）发布一次，ledger 按批次过账控制科目
 * <li>信封：eventType = {@value #EVENT_TYPE}，schemaVersion = {@value #SCHEMA_VERSION}，aggregateType = {@value #AGGREGATE_TYPE}，
 * aggregateId = {@link #aggregateId(AccountEntityType, String, String, int)}（过账流身份），aggregateVersion = postingSequence（同一流连续递增，
 * strictOrder），occurredAt = postedTime，sourceService = account。</li>
 * <li>聚合 ID 格式 {@code {OE|FE}.{entityId}.{currency}.{shardNo}}，例如 {@code OE.AE001.USD.0}、{@code FE.8001.USD.0}；分隔符用 {@code .}
 * 而非 {@code :}，因为 Foundation AggregateKey 的 aggregateId 是消息路由键，且补偿 event_key 以 {@code :} 分四段。</li>
 * <li>事件 JSON 中所有标量都是字符串（ID、序号、版本、行号与金额为十进制，时间为 UTC RFC 3339），缺省值为 JSON null，符合 Foundation
 * JSON Object Profile；Jackson 默认把它们绑定到本类的 Long / Integer / BigDecimal / Instant 字段（同 billing {@code BillingFactPayload}）。</li>
 * <li>事实批次填 fact* 与 financeEntityId，桶移动批次填 movement*，另一组为空；代码类字段以 String 承载稳定 Code，取值见对应枚举。</li>
 */
@Getter
@Setter
public class AccountPostedPayload {
    /** AccountPosted 事件类型，与事件目录一致。 */
    public static final String EVENT_TYPE = "AccountPosted";

    /** 当前 Payload Schema 版本。 */
    public static final int SCHEMA_VERSION = 1;

    /** 事件聚合类型：account 过账流。 */
    public static final String AGGREGATE_TYPE = "ACCOUNT_POSTING";

    /** 聚合 ID 分隔符。 */
    static final String SEPARATOR = ".";

    /** 聚合 ID 中的主体标识：OE entityNo 或 Finance Entity 十进制主键，不含分隔符。 */
    static final Pattern ENTITY_ID = Pattern.compile("[A-Za-z0-9]{1,19}");

    /** 聚合 ID 中的币种。 */
    static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    /** 聚合 ID 中的分片号：非负十进制，无前导零。 */
    static final Pattern SHARD_NO = Pattern.compile("0|[1-9][0-9]{0,8}");

    /** 聚合 ID 最大长度，与 Foundation AggregateKey 及 t_consumed_aggregate_version.aggregate_id 一致。 */
    static final int MAX_AGGREGATE_ID_LENGTH = 40;

    /** 批次主键（t_account_posting.id）。 */
    private Long postingId;

    /** 主体类型，取值见 {@code AccountEntityType}。 */
    private String entityType;

    /** 主体标识：OE 为 entityNo，Finance Entity 为 ledger 本地主键十进制字符串。 */
    private String entityId;

    /** 批次币种，大写 ISO 4217。 */
    private String currency;

    /** 流分片号，从 0 开始。 */
    private Integer shardNo;

    /** 同一流内从 1 开始连续递增的批次序号，即 aggregateVersion。 */
    private Long postingSequence;

    /** 批次来源，取值见 {@code PostingSourceType}。 */
    private String sourceType;

    /** 批次生成的 UTC 微秒时间，即信封 occurredAt。 */
    private Instant postedTime;

    /** 事实批次：计费事实主键；桶移动批次为空。 */
    private Long factId;

    /** 事实批次：计费事实版本；桶移动批次为空。 */
    private Long factVersion;

    /** 事实批次：计费事实类型代码（billing {@code BillingFactType}）；桶移动批次为空。 */
    private String factType;

    /** 事实批次：本事实替代的前一事实；首次登记或桶移动批次为空。 */
    private Long supersedesFactId;

    /** 事实批次：应用结果，取值见 {@code FactApplyStatus}；桶移动批次为空。 */
    private String factApplyStatus;

    /** 事实批次：平台服务费收取方 Finance Entity；事实无合作方或桶移动批次为空。 */
    private Long financeEntityId;

    /** 桶移动批次：移动记录主键（t_balance_movement.id）；事实批次为空。 */
    private Long movementId;

    /** 桶移动批次：移动来源类型，取值见 {@code MovementSourceType}；事实批次为空。 */
    private String movementSourceType;

    /** 桶移动批次：来源业务的稳定引用；事实批次为空。 */
    private String movementSourceRef;

    /** 批次全部流水，按行号升序；无 Owner 影响或 SKIPPED 的事实批次为空列表。 */
    private List<AccountPostedEntryPayload> entries;

    /**
     * 生成过账流的聚合 ID：{@code {OE|FE}.{entityId}.{currency}.{shardNo}}
     *
     * @param entityType 主体类型
     * @param entityId 主体标识，1–19 位字母数字
     * @param currency 大写三字母币种
     * @param shardNo 非负分片号
     * @return 聚合 ID，最长 40 字符
     * @throws IllegalArgumentException 任一参数为空或格式非法
     */
    public static String aggregateId(AccountEntityType entityType, String entityId, String currency, int shardNo) {
        if (entityType == null) {
            throw new IllegalArgumentException("entityType must not be null");
        }
        require(entityId, ENTITY_ID, "entityId");
        require(currency, CURRENCY, "currency");
        if (shardNo < 0) {
            throw new IllegalArgumentException("shardNo must not be negative");
        }
        String aggregateId = entityType.getAggregatePrefix() + SEPARATOR + entityId + SEPARATOR + currency + SEPARATOR
            + shardNo;
        if (aggregateId.length() > MAX_AGGREGATE_ID_LENGTH) {
            throw new IllegalArgumentException("aggregateId exceeds " + MAX_AGGREGATE_ID_LENGTH + " characters");
        }
        return aggregateId;
    }

    /**
     * 解析过账流聚合 ID
     *
     * @param aggregateId 由 {@link #aggregateId(AccountEntityType, String, String, int)} 生成的聚合 ID
     * @return 主体类型、主体标识、币种与分片号
     * @throws IllegalArgumentException 不是四段格式、前缀未知或任一段非法
     */
    public static StreamKey parseAggregateId(String aggregateId) {
        if (aggregateId == null || aggregateId.length() > MAX_AGGREGATE_ID_LENGTH) {
            throw new IllegalArgumentException("aggregateId has an invalid format");
        }
        String[] parts = aggregateId.split(Pattern.quote(SEPARATOR), -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("aggregateId must have four '.' separated parts");
        }
        AccountEntityType entityType = AccountEntityType.ofAggregatePrefix(parts[0]);
        if (entityType == null) {
            throw new IllegalArgumentException("aggregateId has an unknown entity prefix");
        }
        require(parts[1], ENTITY_ID, "entityId");
        require(parts[2], CURRENCY, "currency");
        require(parts[3], SHARD_NO, "shardNo");
        return new StreamKey(entityType, parts[1], parts[2], Integer.parseInt(parts[3]));
    }

    /**
     * 按格式校验必填文本
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

    /** 过账流身份：主体类型、主体标识、币种与分片号。 */
    @Getter
    public static final class StreamKey {
        /** 主体类型。 */
        private final AccountEntityType entityType;

        /** 主体标识。 */
        private final String entityId;

        /** 币种。 */
        private final String currency;

        /** 分片号。 */
        private final int shardNo;

        /** 由 {@link AccountPostedPayload#parseAggregateId(String)} 构造已校验的流身份。 */
        StreamKey(AccountEntityType entityType, String entityId, String currency, int shardNo) {
            this.entityType = entityType;
            this.entityId = entityId;
            this.currency = currency;
            this.shardNo = shardNo;
        }

        /** 还原聚合 ID。 */
        public String aggregateId() {
            return AccountPostedPayload.aggregateId(entityType, entityId, currency, shardNo);
        }
    }
}
