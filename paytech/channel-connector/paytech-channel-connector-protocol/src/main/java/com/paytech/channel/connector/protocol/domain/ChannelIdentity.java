package com.paytech.channel.connector.protocol.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 渠道身份：protocol 发布给支付扩展（core）与入站适配的领域结果，不暴露 t_channel 的 DataObject
 * <li>状态由 {@code ChannelService#changeChannelStatus} 在 SQL 成功后同步；其他字段只读。</li>
 */
@Getter
@AllArgsConstructor
public class ChannelIdentity {
    /** 渠道内部标识。 */
    private final Long channelId;

    /** 渠道编码。 */
    private final String channelCode;

    /** 渠道名称。 */
    private final String channelName;

    /** 渠道类型，创建后不可修改。 */
    private final ChannelType channelType;

    /** 渠道状态。 */
    private ChannelStatus channelStatus;

    /** 渠道说明，未填写时为空。 */
    private final String description;

    /** 本环境出站地址，按保存顺序；未配置为空列表。 */
    private final List<EndpointDefinition> endpoints;

    /** 渠道并发版本。 */
    private final int version;

    /** 创建 UTC 时间。 */
    private final Instant createTime;

    /** 最后变更 UTC 时间。 */
    private Instant updateTime;

    /** 支付结果通知定义 JSON；未配置为 null（不接收通知）。由支付扩展（core）解析与校验。 */
    private final String callbackDefinitionJson;

    /**
     * 由本模块渠道行与已解析的出站地址构造
     *
     * @param row 渠道行
     * @param endpoints 已解析的出站地址，未配置为空列表
     * @return 渠道身份
     */
    public static ChannelIdentity of(Channel row, List<EndpointDefinition> endpoints) {
        return new ChannelIdentity(row.getChannelId(), row.getChannelCode(), row.getChannelName(),
            row.getChannelType(), row.getChannelStatus(), row.getDescription(), List.copyOf(endpoints),
            row.getVersion(), row.getCreateTime(), row.getUpdateTime(), row.getCallbackDefinitionJson());
    }

    /** 是否已关闭；关闭后拒绝一切写入。 */
    public boolean closed() {
        return channelStatus == ChannelStatus.CLOSED;
    }

    /** 是否服务渠道。 */
    public boolean service() {
        return channelType == ChannelType.SERVICE;
    }

    /** 是否已配置出站地址；激活的唯一前置。 */
    public boolean endpointsConfigured() {
        return !endpoints.isEmpty();
    }

    /**
     * 状态写入成功后同步
     *
     * @param status 新状态
     * @param now 写入时间
     */
    public void statusChanged(ChannelStatus status, Instant now) {
        this.channelStatus = status;
        this.updateTime = now;
    }
}
