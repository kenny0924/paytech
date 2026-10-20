package com.paytech.channel.connector.api.channel.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * 渠道技术账号详情
 * <li>不返回 Vault Token 全文、账号参数或凭据；只返回是否已配置及 Token 末 4 位供运营核对。</li>
 */
@Getter
@Setter
public class ChannelAccountResponseBody {
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

    /** 运营备注，可为 null */
    private String remark;

    /** 是否已写入参数与凭据 */
    private boolean configured;

    /** Vault Token 末 4 位；未配置时为 null */
    private String vaultTokenSuffix;

    /** 当前绑定本账号且未关闭的 MID 数 */
    private long boundMidCount;

    /** 创建时间，UTC */
    private Instant createTime;

    /** 最后修改时间，UTC */
    private Instant updateTime;
}
