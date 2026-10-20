package com.paytech.channel.connector.api.channel.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 渠道技术账号列表项；不含 Token、参数或凭据。 */
@Getter
@Setter
public class ChannelAccountSummaryResponseBody {
    /** 技术账号 ID，十进制字符串 */
    private String channelAccountId;

    /** 所属渠道 ID，十进制字符串 */
    private String channelId;

    /** 所属 Operating Entity 编号 */
    private String entityNo;

    /** 账号展示名称 */
    private String accountName;

    /** 账号状态：DRAFT、ACTIVE、SUSPENDED、CLOSED */
    private String status;

    /** 是否已写入参数与凭据 */
    private boolean configured;

    /** Vault Token 末 4 位；未配置时为 null */
    private String vaultTokenSuffix;

    /** 最后修改时间，UTC */
    private Instant updateTime;
}
