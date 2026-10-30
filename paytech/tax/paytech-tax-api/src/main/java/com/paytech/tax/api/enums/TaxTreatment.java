package com.paytech.tax.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 税组件处理类型（t_tax_calculation_component.treatment）；零收税必须有明确处理类型与原因码。 */
public enum TaxTreatment implements CodeEnum {
    /** 应税，按税率收取 */
    TAXABLE,

    /** 零税率 */
    ZERO_RATED,

    /** 免税 */
    EXEMPT,

    /** 不在征税范围 */
    OUT_OF_SCOPE,

    /** 买方反向征税，卖方收取为零 */
    REVERSE_CHARGE,

    /** 有记录但获准不收（market.obligation=NO_COLLECTION_REQUIRED），税额为零 */
    NOT_COLLECTED;

    /** 数据库、接口与消息使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
