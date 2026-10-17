package com.paytech.platform.support.delivery.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 受理后不可变的冻结事件；仅在 Delivery 模块内使用。 */
@Getter
@Setter
public class WebhookEvent {

    /** Platform 内部事件主键，不对外。 */
    private Long eventId;

    /** 所属 CLIENT 租户。 */
    private Long tenantId;

    /** 来源调用方服务名。 */
    private String sourceSystem;

    /** 公开 eventId。 */
    private String sourceEventKey;

    /** 公开事件类型。 */
    private String eventType;

    /** Envelope 的 schemaVersion。 */
    private int payloadSchemaVersion;

    /** 来源资源版本，只用于重复比较。 */
    private int sourceVersion;

    /** data.object 规范字节摘要。 */
    private String sourcePayloadHash;

    /** Envelope 规范字节摘要。 */
    private String payloadHash;

    /** 冻结的 Envelope JSON；数据库读出时属性顺序可能变化，发送前须重新规范化。 */
    private String payload;

    /** UTC 微秒创建时间。 */
    private Instant createTime;
}
