package com.paytech.tax.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 单据报送状态（t_tax_document.submission_status）；本期为 NOT_REQUIRED。 */
public enum DocumentSubmissionStatus implements CodeEnum {
    /** 无须报送 */
    NOT_REQUIRED,

    /** 待报送 */
    PENDING,

    /** 已接受 */
    ACCEPTED,

    /** 被拒绝 */
    REJECTED,

    /** 结果未知 */
    UNKNOWN;

    /** 数据库使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
