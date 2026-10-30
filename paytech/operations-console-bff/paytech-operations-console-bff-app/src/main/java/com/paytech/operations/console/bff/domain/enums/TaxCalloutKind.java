package com.paytech.operations.console.bff.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 税务查询页提示卡类型及色调；每页只显示当前最需要注意的一件事。 */
@Getter
@AllArgsConstructor
public enum TaxCalloutKind implements CodeEnum {
    /** 税由 Client 负责，不是免税 */
    CLIENT_RESPONSIBLE("NEUTRAL"),

    /** 当前报价被阻断，不会以零税继续收款 */
    PAYMENT_BLOCKED("DANGER"),

    /** 底稿缺法定汇率，只影响申报，不影响收款和入账 */
    REPORTING_FX_MISSING("WARNING"),

    /** 已登记，税票尚未开具 */
    DOCUMENT_PENDING("INFO"),

    /** 已登记、已开票、底稿就绪 */
    REGISTERED("SUCCESS"),

    /** 只有建单暂估，暂估报价不可支付 */
    ESTIMATE_ONLY("INFO"),

    /** 最终报价待支付 */
    AWAITING_PAYMENT("INFO");

    /** 提示卡色调：INFO、SUCCESS、WARNING、DANGER、NEUTRAL */
    private final String tone;

    /** 页面模型使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
