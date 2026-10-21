package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 清算账户状态（t_clearing_account.status）
 * <li>停用后不再接收新的平台流水和渠道账单。</li>
 */
public enum ClearingAccountStatus implements CodeEnum {
    /** 启用 */
    ACTIVE,

    /** 停用：不再接收新的平台流水和渠道账单，也不参与清算账户解析 */
    INACTIVE;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
