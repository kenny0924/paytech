package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 归类码（t_recon_resolution.classification_code），科目映射在 ledger 规则包
 * <li>按异常来源对象可用：渠道账单行 CHANNEL_FEE_OTHER、CHANNEL_RESERVE_HOLD、CHANNEL_RESERVE_RELEASE、RECON_DIFFERENCE；平台流水 RECON_DIFFERENCE；预期银行流水 BANK_FEE、RECON_DIFFERENCE；银行流水 BANK_FEE、BANK_INTEREST、INTERNAL_TRANSFER、UNALLOCATED_RECEIPT、PAYOUT_RETURN_PENDING、RECON_DIFFERENCE。</li>
 * <li>新增归类码只需加枚举值与 ledger 规则包映射。</li>
 */
public enum ReconClassification implements CodeEnum {
    /** 交易层：渠道其他费用（如月费） */
    CHANNEL_FEE_OTHER,

    /** 交易层：渠道保证金扣留 */
    CHANNEL_RESERVE_HOLD,

    /** 交易层：渠道保证金释放 */
    CHANNEL_RESERVE_RELEASE,

    /** 银行层：银行手续费 */
    BANK_FEE,

    /** 银行层：银行利息 */
    BANK_INTEREST,

    /** 银行层：自有账户调拨，两边各归类一次 */
    INTERNAL_TRANSFER,

    /** 银行层：暂不能认定的收款，进待处理负债 */
    UNALLOCATED_RECEIPT,

    /** 银行层：出款被退回，待处理 */
    PAYOUT_RETURN_PENDING,

    /** 通用：对账差异 */
    RECON_DIFFERENCE;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
