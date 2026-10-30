package com.paytech.tax.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 法定承担角色（t_tax_calculation_component.liable_role）。 */
public enum LiableRole implements CodeEnum {
    /** 卖方收取并缴纳 */
    SELLER,

    /** 买方自行计税 */
    BUYER;

    /** 数据库使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
