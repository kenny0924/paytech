package com.paytech.payout.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 核验拒绝原因（受控目录，technique 7.4）
 */
public enum BeneficiaryRejectReason implements CodeEnum {
    /** 户名与法定名不一致 */
    HOLDER_NAME_MISMATCH,

    /** 账号无效 */
    INVALID_ACCOUNT,

    /** 银行不支持 */
    UNSUPPORTED_BANK,

    /** 需要补充证明材料 */
    DOCUMENT_REQUIRED,

    /** 其他 */
    OTHER;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
