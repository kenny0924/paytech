package com.paytech.billing.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 明细行链接类型（t_billing_fact_line_link.link_type）：新行对原行的冲回或调整。 */
public enum LineLinkType implements CodeEnum {
    /** 冲回原行，同一原行累计不得超过原行 postedAmount */
    REVERSAL,

    /** 调整原行 */
    ADJUSTMENT;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
