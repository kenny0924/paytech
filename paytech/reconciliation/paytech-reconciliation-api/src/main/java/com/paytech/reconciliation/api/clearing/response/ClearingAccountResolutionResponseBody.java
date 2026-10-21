package com.paytech.reconciliation.api.clearing.response;

import lombok.Getter;
import lombok.Setter;

/** 清算账户解析结果：billing 以 clearingAccountId 作为 CLEARING_ACCOUNT 引用。 */
@Getter
@Setter
public class ClearingAccountResolutionResponseBody {
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
}
