package com.paytech.reconciliation.api.clearing.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 清算账户列表项。 */
@Getter
@Setter
public class ClearingAccountSummaryResponseBody {
    /** 清算账户主键 */
    private String clearingAccountId;

    /** 清算账户编码 */
    private String clearingAccountCode;

    /** 渠道编码 */
    private String channelCode;

    /** 经营主体编号 */
    private String entityNo;

    /** 清算币种 */
    private String currency;

    /**
     * 到账银行账户主键
     * <li>未配置为空。</li>
     */
    private String settlementCashAccountId;

    /** 状态：ACTIVE、INACTIVE */
    private String status;

    /** 已挂入的 MID 数 */
    private Integer midCount;

    /** 最后更新的 UTC 时间 */
    private Instant updateTime;
}
