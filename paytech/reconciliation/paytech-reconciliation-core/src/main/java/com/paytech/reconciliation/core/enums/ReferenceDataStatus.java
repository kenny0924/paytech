package com.paytech.reconciliation.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 渠道清算账户等基础资料能否被新业务使用
 * <li>副本：Owner 为 ledger 的同名枚举，本服务只用于 t_channel_account.status；对账切片落地时按本服务语义收口。</li>
 */
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
