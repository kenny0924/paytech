package com.paytech.reconciliation.core.dal.dataobject;

import java.time.Instant;

import com.paytech.reconciliation.core.enums.ReferenceDataStatus;

import lombok.Getter;
import lombok.Setter;

/** t_channel_account：单一经营主体、渠道商户号和币种下的 Finance 渠道清算账户，与 Payment 技术接口账户相互独立。 */
@Getter
@Setter
public class ChannelAccount {
    /**
     * 渠道清算账户 Snowflake 主键
     */
    private Long id;

    /**
     * Payment 渠道稳定标识
     */
    private Long channelId;

    /**
     * Payment 渠道商户号稳定标识
     */
    private Long midId;

    /**
     * 所属经营主体编号
     */
    private String entityNo;

    /**
     * 币种
     */
    private String currency;

    /**
     * 可用状态
     * <li>允许值：ACTIVE、INACTIVE。</li>
     */
    private ReferenceDataStatus status;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该可变记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
