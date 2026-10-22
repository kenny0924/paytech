package com.paytech.channel.connector.api.channel.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 渠道列表项。 */
@Getter
@Setter
public class ChannelSummaryResponseBody {
    /** 渠道 ID，十进制字符串 */
    private String channelId;

    /** 渠道编码 */
    private String channelCode;

    /** 渠道名称 */
    private String channelName;

    /** 渠道类型：PAYMENT、SERVICE */
    private String channelType;

    /** 渠道状态：DRAFT、ACTIVE、SUSPENDED、CLOSED */
    private String status;

    /** 渠道下全部技术账号数（含已关闭）；服务渠道即凭据数 */
    private long accountCount;

    /** 渠道下全部 MID 数（含已关闭）；服务渠道恒为 0 */
    private long midCount;

    /** 已配置的出站地址数；未配置为 0 */
    private int endpointCount;

    /**
     * ACTIVE 调用授权数
     * <li>只对服务渠道统计；支付渠道为 null。</li>
     */
    private Long activeGrantCount;

    /** 最后修改时间，UTC */
    private Instant updateTime;
}
