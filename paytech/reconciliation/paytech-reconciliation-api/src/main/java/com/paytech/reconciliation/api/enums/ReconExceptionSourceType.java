package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 对账异常挂靠的本库对象类型（t_recon_exception.source_type）
 * <li>决定可用处置与归类码；人工开立异常只允许 PLATFORM_TXN、EXPECTED_BANK_MOVEMENT、BANK_STATEMENT_LINE。</li>
 */
public enum ReconExceptionSourceType implements CodeEnum {
    /** 渠道账单 */
    CHANNEL_STATEMENT,

    /** 渠道账单行 */
    CHANNEL_STATEMENT_LINE,

    /** 平台流水 */
    PLATFORM_TXN,

    /** 银行对账单 */
    BANK_STATEMENT,

    /** 银行流水 */
    BANK_STATEMENT_LINE,

    /** 预期银行流水 */
    EXPECTED_BANK_MOVEMENT;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
