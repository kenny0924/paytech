package com.paytech.billing.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 计费事实来源方式（t_billing_fact.origin_type），决定是否走 Maker-Checker。 */
public enum FactOriginType implements CodeEnum {
    /** 由事件或系统命令生成，登记即 APPROVED */
    AUTO,

    /** 人工登记，DRAFT 经 Checker 批准 */
    MANUAL;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
