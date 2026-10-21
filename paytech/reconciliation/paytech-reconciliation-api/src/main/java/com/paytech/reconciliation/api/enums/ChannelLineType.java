package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 渠道账单行类型（t_channel_statement_line.line_type）
 * <li>保证金扣留 / 释放在真实渠道样本出来前按 ADJUSTMENT 处理。</li>
 */
public enum ChannelLineType implements CodeEnum {
    /** 支付清算行，对应应收平台流水 */
    PAYMENT,

    /** 退款清算行，对应应付平台流水 */
    REFUND,

    /** 拒付扣款行，对应应付平台流水 */
    CHARGEBACK,

    /** 费用行：带外部交易号的并入交易行实际费用，不带的开 UNEXPLAINED_ADJUSTMENT 待归类 */
    FEE,

    /** 调整行：必须显式声明方向，开 UNEXPLAINED_ADJUSTMENT 待归类 */
    ADJUSTMENT;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
