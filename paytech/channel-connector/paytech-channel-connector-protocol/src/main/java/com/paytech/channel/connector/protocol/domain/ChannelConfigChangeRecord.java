package com.paytech.channel.connector.protocol.domain;

import java.time.Instant;

import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;

import lombok.Getter;
import lombok.Setter;

/**
 * 渠道配置变更记录（protocol 对外发布的领域对象）
 * <li>core 的渠道、账号、MID、能力与成本写入经 {@code ChannelConfigChangeService} 在同一本地事务中写入，不接触持久化对象。</li>
 * <li>只增不改；主键与创建时间由调用方在构造时分配。</li>
 */
@Getter
@Setter
public class ChannelConfigChangeRecord {
    /**
     * 变更记录的 Snowflake 主键
     */
    private Long id;

    /**
     * 被变更对象类型
     */
    private ChangeTargetType targetType;

    /**
     * 被变更对象在本库的主键
     */
    private Long targetId;

    /**
     * 变更类型
     */
    private ChangeType changeType;

    /**
     * 状态变更前的状态代码
     * <li>非状态变更为空。</li>
     */
    private String fromStatus;

    /**
     * 状态变更后的状态代码
     * <li>非状态变更为空。</li>
     */
    private String toStatus;

    /**
     * 执行变更的 Platform Actor 稳定业务 ID
     */
    private Long operatorId;

    /**
     * 操作者填写的非敏感原因
     * <li>可为空；禁止填写凭据。</li>
     */
    private String reason;

    /**
     * 外部 API 配置变更的字段摘要 JSON
     * <li>只有外部 API 配置变更填写；不含定义原文、参数或凭据值。</li>
     */
    private String changeDetail;

    /**
     * 该记录在本库中的 UTC 微秒创建时间
     */
    private Instant createTime;
}
