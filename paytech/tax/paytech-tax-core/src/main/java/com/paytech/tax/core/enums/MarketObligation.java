package com.paytech.tax.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 准入评估结论（t_tax_market.obligation）。 */
public enum MarketObligation implements CodeEnum {
    /** 按地区包计税，必须指向有效注册 */
    REGISTRATION_REQUIRED,

    /** 获准不收，组件 NOT_COLLECTED、税 0 */
    NO_COLLECTION_REQUIRED,

    /** 买方自行计税；本期按未启用阻断 */
    CUSTOMER_ACCOUNTED,

    /** 待复核；本期按未启用阻断 */
    REVIEW_REQUIRED;

    /** 数据库使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
