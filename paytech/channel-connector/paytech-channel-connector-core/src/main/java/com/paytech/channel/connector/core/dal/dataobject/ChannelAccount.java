package com.paytech.payment.channel.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;
import com.paytech.payment.channel.core.enums.ChannelStatus;


/** ChannelAccount 对应的 Channel 所属持久化对象。 */
@Getter
@Setter
public class ChannelAccount {
    /** 技术账号标识。 */
    private Long channelAccountId;

    /** 所属渠道标识。 */
    private Long channelId;

    /** CCT 拥有的不可变 OE 编号。 */
    private String entityNo;

    /** 非唯一展示名称。 */
    private String accountName;

    /** 账号配置 Vault 引用，DRAFT 可空，不输出到日志。 */
    private String accountVaultToken;

    /** 账号生命周期状态。 */
    private ChannelStatus accountStatus;

    /** 非敏感运营备注，可空。 */
    private String remark;

    /** 创建 UTC 时间，微秒精度。 */
    private Instant createTime;

    /** 最后变更 UTC 时间，微秒精度。 */
    private Instant updateTime;
}
