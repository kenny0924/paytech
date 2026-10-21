package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 账单行与银行流水的匹配状态（t_channel_statement_line.match_status、t_bank_statement_line.match_status）
 * <li>账单行被归类或作为 FEE 行并入交易行也记 MATCHED；PARTIALLY_MATCHED、SUSPENDED 只用于银行流水，SUSPENDED 切片 2 起使用。</li>
 */
public enum LineMatchStatus implements CodeEnum {
    /** 尚未匹配 */
    UNMATCHED,

    /** 银行流水已部分确认匹配 */
    PARTIALLY_MATCHED,

    /** 已匹配或已归类结清 */
    MATCHED,

    /** 存在未结异常 */
    EXCEPTION,

    /** 未识别借方银行流水已暂记（切片 2） */
    SUSPENDED;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
