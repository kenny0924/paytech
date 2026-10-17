package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 渠道成本方案稳定身份的生命周期状态。 */
public enum ChannelCostPlanStatus implements CodeEnum {
    /** 已创建、尚无生效版本 */
    DRAFT,

    /** 已有当前生效版本 */
    ACTIVE,

    /** 已停用，本期不提供停用入口 */
    INACTIVE;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
