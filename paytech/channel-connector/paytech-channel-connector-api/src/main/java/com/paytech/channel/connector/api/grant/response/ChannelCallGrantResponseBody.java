package com.paytech.channel.connector.api.grant.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 调用授权详情与列表项。 */
@Getter
@Setter
public class ChannelCallGrantResponseBody {
    /** 授权 ID，十进制字符串 */
    private String callGrantId;

    /** 服务渠道 ID，十进制字符串 */
    private String channelId;

    /** 调用方服务名 */
    private String callerService;

    /** 标准 API 编码 */
    private String standardApiCode;

    /** 标准 API 名称 */
    private String standardApiName;

    /** 授权状态：ACTIVE、DISABLED */
    private String grantStatus;

    /** 授权并发版本，启停时作为 expectedVersion 回传 */
    private int version;

    /** 创建授权的 Account ID，十进制字符串 */
    private String createdBy;

    /** 最后修改授权的 Account ID，十进制字符串 */
    private String updatedBy;

    /** 创建时间，UTC */
    private Instant createTime;

    /** 最后修改时间，UTC */
    private Instant updateTime;
}
