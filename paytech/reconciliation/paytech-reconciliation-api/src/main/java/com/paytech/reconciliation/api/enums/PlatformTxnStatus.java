package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 平台流水逐笔对账进度（t_platform_txn.status）
 */
public enum PlatformTxnStatus implements CodeEnum {
    /** 尚未被任何账单行覆盖 */
    OPEN,

    /** 已被账单行部分覆盖 */
    PARTIALLY_MATCHED,

    /** 已被账单行完全覆盖，等待所在账单对平 */
    MATCHED,

    /** 所在账单已对平并随对账事件登记，或经归类处置结清 */
    SETTLED,

    /** 存在未结异常 */
    EXCEPTION;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
