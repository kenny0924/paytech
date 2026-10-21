package com.paytech.channel.connector.protocol.domain;

import java.time.Instant;

import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.protocol.core.enums.ChangeType;
import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 渠道配置变更记录的纯构造
 * <li>只组装待写入对象，不生成主键、不读取时间、不访问数据库；由用例 Processor 在业务变更的同一事务中经变更记录服务写入。</li>
 * <li>原因由操作者填写且禁止含凭据；配置写入（CONFIGURATION）不记录任何参数或凭据内容。</li>
 */
public final class ChannelConfigChanges {

    /** 工具类不实例化。 */
    private ChannelConfigChanges() {
    }

    /**
     * 非状态变更记录（创建、字段更新、配置写入、改绑）
     *
     * @param id 已分配的记录主键
     * @param targetType 被变更对象类型
     * @param targetId 被变更对象主键
     * @param changeType 变更类型
     * @param operatorId 操作者 Account ID
     * @param now 本次写入时间
     * @return 待写入记录，状态字段为空
     */
    public static ChannelConfigChangeRecord of(long id, ChangeTargetType targetType, long targetId, ChangeType changeType,
        long operatorId, Instant now) {
        ChannelConfigChangeRecord change = new ChannelConfigChangeRecord();
        change.setId(id);
        change.setTargetType(targetType);
        change.setTargetId(targetId);
        change.setChangeType(changeType);
        change.setOperatorId(operatorId);
        change.setCreateTime(now);
        return change;
    }

    /**
     * 状态变更记录
     *
     * @param id 已分配的记录主键
     * @param targetType 被变更对象类型
     * @param targetId 被变更对象主键
     * @param from 变更前状态
     * @param to 变更后状态
     * @param operatorId 操作者 Account ID
     * @param reason 操作者原因，可空
     * @param now 本次写入时间
     * @return 待写入记录
     */
    public static ChannelConfigChangeRecord status(long id, ChangeTargetType targetType, long targetId, CodeEnum from,
        CodeEnum to, long operatorId, String reason, Instant now) {
        ChannelConfigChangeRecord change = of(id, targetType, targetId, ChangeType.STATUS, operatorId, now);
        change.setFromStatus(from.getCode());
        change.setToStatus(to.getCode());
        change.setReason(reason);
        return change;
    }
}
