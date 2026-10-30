package com.paytech.payout.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 关闭原因（受控目录，technique 7.4）
 */
public enum BeneficiaryCloseReason implements CodeEnum {
    /** Owner 申请 */
    OWNER_REQUEST,

    /** 已被新账户替代 */
    REPLACED,

    /** 运营关闭 */
    OPS;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
