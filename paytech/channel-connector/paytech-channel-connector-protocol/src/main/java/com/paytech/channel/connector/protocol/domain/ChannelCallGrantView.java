package com.paytech.channel.connector.protocol.domain;

import java.time.Instant;

import com.paytech.channel.connector.api.enums.ChannelCallGrantStatus;

import lombok.Getter;
import lombok.Setter;

/** 调用授权查询结果：授权行与同库标准 API 的编码、名称组合。 */
@Getter
@Setter
public class ChannelCallGrantView {
    /** 授权内部标识。 */
    private Long callGrantId;

    /** 服务渠道标识。 */
    private Long channelId;

    /** 调用方服务名。 */
    private String callerService;

    /** 标准 API 编码。 */
    private String standardApiCode;

    /** 标准 API 名称。 */
    private String standardApiName;

    /** 授权状态。 */
    private ChannelCallGrantStatus grantStatus;

    /** 授权并发版本。 */
    private int version;

    /** 创建授权的 Operations 账号标识。 */
    private Long createdBy;

    /** 最后修改授权的 Operations 账号标识。 */
    private Long updatedBy;

    /** 创建 UTC 时间。 */
    private Instant createTime;

    /** 最后变更 UTC 时间。 */
    private Instant updateTime;
}
