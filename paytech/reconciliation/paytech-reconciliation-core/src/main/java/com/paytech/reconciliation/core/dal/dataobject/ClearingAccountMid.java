package com.paytech.reconciliation.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * t_clearing_account_mid（清算账户MID）
 * <li>清算账户所含的 MID；billing 按 MID + 币种解析清算账户。摘除 MID 即删除本行，已生成的平台流水仍属原清算账户。</li>
 */
@Getter
@Setter
public class ClearingAccountMid {
    /**
     * MID归属ID
     * <li>Snowflake 主键。</li>
     */
    private Long id;

    /**
     * 清算账户ID
     * <li>所属清算账户。</li>
     */
    private Long clearingAccountId;

    /**
     * MID ID
     * <li>channel-connector 的 MID 主键，不建跨库外键。</li>
     */
    private Long midId;

    /**
     * 币种
     * <li>从清算账户冗余的币种，用于保证同一 MID + 币种只归属一个清算账户。取值来源：ISO 4217 币种代码；非固定枚举。</li>
     */
    private String currency;

    /**
     * 创建时间
     * <li>挂入的 UTC 微秒时间。</li>
     */
    private Instant createTime;
}
