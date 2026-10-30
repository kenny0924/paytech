package com.paytech.tax.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 证据核验状态（t_tax_evidence.validation_status）；本期国家证据均为 NOT_CHECKED。 */
public enum EvidenceValidationStatus implements CodeEnum {
    /** 已核验有效 */
    VALID,

    /** 已核验无效 */
    INVALID,

    /** 核验结果未知 */
    UNKNOWN,

    /** 未核验 */
    NOT_CHECKED;

    /** 数据库使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
