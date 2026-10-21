package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 对账异常所属层（t_recon_exception.recon_layer）
 */
public enum ReconLayer implements CodeEnum {
    /** 交易层：平台流水与渠道账单 */
    TRANSACTION,

    /** 银行层：预期银行流水与银行流水 */
    BANK;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
