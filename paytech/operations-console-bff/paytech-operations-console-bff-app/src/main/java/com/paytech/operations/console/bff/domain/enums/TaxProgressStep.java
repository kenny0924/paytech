package com.paytech.operations.console.bff.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 税务进度条的步骤；MoR 订单五步，不由平台计税的订单为 QUOTE、PAYMENT、REGISTRATION 三步。 */
public enum TaxProgressStep implements CodeEnum {
    /** 建单报价（首个计算） */
    QUOTE,

    /** 消费者提交资料后的最终报价 */
    RECALCULATION,

    /** 支付成功（以 tax 正式交易为准） */
    PAYMENT,

    /** 正式登记与开票 */
    REGISTRATION,

    /** 申报底稿 */
    REPORTING;

    /** 页面模型使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
