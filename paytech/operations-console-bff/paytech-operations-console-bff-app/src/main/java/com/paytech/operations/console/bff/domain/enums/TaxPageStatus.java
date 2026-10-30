package com.paytech.operations.console.bff.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 税务查询页标题处的唯一状态徽标；由 BFF 从当前报价、正式交易与税票推导，页面经 tax.enum.TaxPageStatus.* 显示。 */
public enum TaxPageStatus implements CodeEnum {
    /** 当前报价 NOT_MANAGED：税由 Client 负责 */
    NOT_MANAGED,

    /** 当前报价被阻断，订单不可支付 */
    BLOCKED,

    /** 只有建单暂估报价，消费者尚未提交资料 */
    ESTIMATED,

    /** 已有最终报价，尚未支付登记 */
    QUOTED,

    /** 已正式登记，税票尚未开具 */
    REGISTERED,

    /** 已正式登记且税票已开具 */
    ISSUED;

    /** 页面模型使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
