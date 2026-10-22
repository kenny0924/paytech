package com.paytech.channel.connector.api.channel.response;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 渠道详情。 */
@Getter
@Setter
public class ChannelResponseBody {
    /** 渠道 ID，十进制字符串 */
    private String channelId;

    /** 渠道编码，不可修改 */
    private String channelCode;

    /** 渠道名称 */
    private String channelName;

    /** 渠道类型：PAYMENT、SERVICE，不可修改 */
    private String channelType;

    /** 渠道描述；未填写时为 null */
    private String description;

    /**
     * 本环境出站地址
     * <li>按保存顺序；未配置时为空数组。</li>
     */
    private List<ChannelEndpointDetail> endpoints;

    /**
     * 渠道并发版本
     * <li>名称、描述或出站地址保存后递增；修改出站地址时作为 expectedVersion 回传。</li>
     */
    private int version;

    /** 渠道状态：DRAFT、ACTIVE、SUSPENDED、CLOSED */
    private String status;

    /** 创建时间，UTC */
    private Instant createTime;

    /** 最后修改时间，UTC */
    private Instant updateTime;
}
