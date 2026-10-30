package com.paytech.payout.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 停用原因（受控目录，technique 7.4）
 */
public enum BeneficiarySuspendReason implements CodeEnum {
    /** 风险审查 */
    RISK_REVIEW,

    /** Owner 申请 */
    OWNER_REQUEST,

    /** 出款失败（S2 / S3 使用） */
    PAYOUT_FAILURE,

    /** 其他 */
    OTHER;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
