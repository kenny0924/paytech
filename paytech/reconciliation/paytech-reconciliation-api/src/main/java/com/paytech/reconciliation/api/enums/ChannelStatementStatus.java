package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 渠道账单状态（t_channel_statement.status）
 * <li>分段导入：开始导入为 RECEIVING；完成导入校验通过进入 MATCHING，不通过整单 REJECTED（留档）。</li>
 * <li>净额方向为 NONE 的账单对平后直接 CASH_CONFIRMED。</li>
 */
public enum ChannelStatementStatus implements CodeEnum {
    /** 分段接收明细行中，尚未完成导入 */
    RECEIVING,

    /** 完成导入时校验不通过，整单拒绝，留档不可再用 */
    REJECTED,

    /** 导入完成，逐行匹配与异常处置中 */
    MATCHING,

    /** 已作废：行匹配与费用失效，平台流水回到未匹配；只允许 RECEIVING、MATCHING 作废 */
    VOIDED,

    /** 已对平并生成预期银行流水，等待银行确认 */
    RECONCILED,

    /** 净额已由银行确认或归类结清 */
    CASH_CONFIRMED;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
