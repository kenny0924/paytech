package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 对账异常类型（t_recon_exception.exception_type）
 * <li>切片 1 实际产生：CHANNEL_LINE_UNMATCHED、AMBIGUOUS_REFERENCE、AMOUNT_MISMATCH、DUPLICATE_SETTLEMENT、UNEXPLAINED_ADJUSTMENT、COST_DEVIATION、BANK_STATEMENT_GAP，以及人工开立的 PLATFORM_ITEM_UNSETTLED、BANK_AMOUNT_MISMATCH、BANK_LINE_UNMATCHED；其余先登记、不产生。</li>
 * <li>不阻塞账单对平的：PLATFORM_ITEM_UNSETTLED、BANK_MOVEMENT_OVERDUE、BANK_STATEMENT_GAP 及全部银行层异常。</li>
 */
public enum ReconExceptionType implements CodeEnum {
    /** 交易层：账单行等待期满仍未命中平台流水（渠道有、平台没有） */
    CHANNEL_LINE_UNMATCHED,

    /** 交易层：账单行按外部交易号命中多条平台流水 */
    AMBIGUOUS_REFERENCE,

    /** 交易层：账单行金额超过平台流水剩余金额 */
    AMOUNT_MISMATCH,

    /** 交易层：同一清算账户未作废账单中已有完全相同的行 */
    DUPLICATE_SETTLEMENT,

    /** 交易层：ADJUSTMENT 行或不带交易号的 FEE 行，待人工归类 */
    UNEXPLAINED_ADJUSTMENT,

    /** 交易层：实际渠道费与预估之差超过清算账户容差 */
    COST_DEVIATION,

    /** 交易层：平台流水长期未结算（切片 1 人工开立，不阻塞） */
    PLATFORM_ITEM_UNSETTLED,

    /** 银行层：银行流水未能匹配预期银行流水（切片 1 人工开立） */
    BANK_LINE_UNMATCHED,

    /** 银行层：预期银行流水到账金额不足或超出（切片 1 人工开立） */
    BANK_AMOUNT_MISMATCH,

    /** 银行层：预期银行流水逾期未到（切片 2） */
    BANK_MOVEMENT_OVERDUE,

    /** 银行层：与上一张对账单的期末余额或期间接不上，提示漏传，不阻塞 */
    BANK_STATEMENT_GAP,

    /** 银行层：迟到扣款（切片 3） */
    LATE_DEBIT;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
