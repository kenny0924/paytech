package com.paytech.account.api.event;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * BalanceMoved Schema v1 Payload（design/04 §5，事件目录 catalog/events.yaml）：account 作为桶移动命令 Owner 执行 AVAILABLE → LOCKED 等移动后发布，
 * ledger 消费并补记分录（sourceType = {@value #ECHO_SOURCE_TYPE}）
 * <li>信封：eventType = {@value #EVENT_TYPE}，schemaVersion = {@value #SCHEMA_VERSION}，aggregateType = {@value #AGGREGATE_TYPE}，
 * aggregateId = 账簿主体身份（bookEntityType:bookEntityId:currency），aggregateVersion = movementSequence（同一账簿连续递增，strictOrder）。</li>
 * <li>事件 JSON 中标量一律为字符串（ID、序号、金额为十进制，时间为 UTC RFC 3339），符合 Foundation JSON Object Profile。</li>
 * <li>阶段 0 只定义 Schema，无生产调用方。</li>
 */
@Getter
@Setter
public class BalanceMovedPayload {
    /** 事件类型，与事件目录一致。 */
    public static final String EVENT_TYPE = "BalanceMoved";

    /** 当前 Payload Schema 版本。 */
    public static final int SCHEMA_VERSION = 1;

    /** 事件聚合类型：账户服务的账簿主体余额。 */
    public static final String AGGREGATE_TYPE = "ACCOUNT_BOOK";

    /** ledger 补记分录的来源类型。 */
    public static final String ECHO_SOURCE_TYPE = "BALANCE_MOVEMENT_ECHO";

    /** 移动命令记录主键（t_balance_movement.id）。 */
    private Long movementId;

    /** 同一账簿主体与币种内连续递增的移动序号，即 aggregateVersion。 */
    private Long movementSequence;

    /** 账簿主体类型：OPERATING_ENTITY 或 FINANCE_ENTITY。 */
    private String bookEntityType;

    /** 账簿主体标识：OE 为 entityNo，Finance Entity 为 ledger 本地主键十进制字符串。 */
    private String bookEntityId;

    /** 移动金额的币种。 */
    private String currency;

    /** 移动所在的标准科目代码（CLIENT_PAYABLE 或 PARTNER_PAYABLE）。 */
    private String accountCode;

    /** 核算对象类型：CLIENT 或 LOCAL_PARTNER。 */
    private String ownerType;

    /** 核算对象规范十进制标识。 */
    private String ownerId;

    /** 转出余额桶。 */
    private String fromBucket;

    /** 转入余额桶。 */
    private String toBucket;

    /** 已按币种精度归整的正移动金额。 */
    private BigDecimal amount;

    /** 发起移动的业务来源类型（如出款锁定、成熟释放）。 */
    private String sourceType;

    /** 来源业务的稳定引用。 */
    private String sourceRef;

    /** 移动完成的 UTC 时间，即信封 occurredAt。 */
    private Instant movedTime;
}
