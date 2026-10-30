package com.paytech.operations.console.bff.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 税务进度条单步的状态。 */
public enum TaxProgressState implements CodeEnum {
    /** 已完成 */
    DONE,

    /** 被阻断（当前报价无法计税） */
    BLOCKED,

    /** 已发生但有待补事项（税票未开具、底稿缺法定汇率） */
    ATTENTION,

    /** 尚未发生 */
    PENDING,

    /** 本订单不适用（不由平台计税时的平台登记） */
    NOT_APPLICABLE,

    /** 税务服务不记录该步（不由平台计税时的支付结果） */
    UNTRACKED;

    /** 页面模型使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
