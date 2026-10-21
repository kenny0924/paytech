package com.paytech.channel.connector.protocol.core.dal.dataobject;

import java.time.Instant;

import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;

import lombok.Getter;
import lombok.Setter;

/** t_channel_config_change：渠道配置、成本版本与外部 API 配置的人工变更留痕；与业务变更同事务写入，只增不改。 */
@Getter
@Setter
public class ChannelConfigChange {
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
     * <li>{objectCode, beforeVersion, afterVersion, changes[{field, before, after}]}；只记名称、编码与 SHA-256 摘要，不含定义原文、参数或凭据值。</li>
     * <li>渠道、账号、MID、能力与成本版本的变更为空。</li>
     */
    private String changeDetail;

    /**
     * 该记录在本库中的 UTC 微秒创建时间
     */
    private Instant createTime;
}
