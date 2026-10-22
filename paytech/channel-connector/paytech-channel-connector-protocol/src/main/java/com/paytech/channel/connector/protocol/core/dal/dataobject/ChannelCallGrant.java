package com.paytech.channel.connector.protocol.core.dal.dataobject;

import java.time.Instant;

import com.paytech.channel.connector.api.enums.ChannelCallGrantStatus;

import lombok.Getter;
import lombok.Setter;

/** t_channel_call_grant：服务渠道的调用授权（调用方服务 × 渠道 × 标准 API），只停用、不删除。 */
@Getter
@Setter
public class ChannelCallGrant {
    /** 授权内部标识。 */
    private Long callGrantId;

    /** 调用方服务名，格式 [a-z][a-z0-9-]{1,63}。 */
    private String callerService;

    /** 被授权调用的服务渠道标识。 */
    private Long channelId;

    /** 被授权调用的标准 API 标识。 */
    private Long standardApiId;

    /** 授权状态。 */
    private ChannelCallGrantStatus grantStatus;

    /** 启用、停用时递增的并发版本，初始为 1。 */
    private int version;

    /** 创建授权的 Operations 账号标识。 */
    private Long createdBy;

    /** 最后修改授权的 Operations 账号标识。 */
    private Long updatedBy;

    /** 创建 UTC 时间，微秒精度。 */
    private Instant createTime;

    /** 最后变更 UTC 时间，微秒精度。 */
    private Instant updateTime;
}
