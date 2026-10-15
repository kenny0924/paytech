package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 币种规则、科目表等基础资料能否被新业务使用。 */
public enum ReferenceDataStatus implements CodeEnum {
    /** 草稿 */
    DRAFT,

    /** 可用 */
    ACTIVE,

    /** 停用 */
    INACTIVE;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
