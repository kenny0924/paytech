package com.paytech.tax.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 正式交易的底稿完整性（t_tax_transaction.reporting_status）；不否定已发生事实。 */
public enum TaxReportingStatus implements CodeEnum {
    /** 底稿未齐，原因见 reportingBlockReason（本期为 FX_RATE_MISSING：申报币种与交易币种不同且未接入法定汇率） */
    PENDING,

    /** 每个应税组件都已生成 OUTPUT 底稿 */
    READY;

    /** 数据库、接口与消息使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
