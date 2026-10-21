package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 对账异常状态（t_recon_exception.status）
 */
public enum ReconExceptionStatus implements CodeEnum {
    /** 待处置 */
    OPEN,

    /** 已提交处置，待复核 */
    UNDER_REVIEW,

    /** 处置已批准并应用 */
    RESOLVED,

    /** 未经处置自然消解，或所属账单作废 */
    AUTO_CLOSED;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
