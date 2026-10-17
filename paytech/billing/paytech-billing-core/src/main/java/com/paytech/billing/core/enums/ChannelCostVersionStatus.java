package com.paytech.billing.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 渠道成本方案版本的审批与生效状态。 */
public enum ChannelCostVersionStatus implements CodeEnum {
    /** 草稿，适用范围与成本组成已随版本一次写入 */
    DRAFT,

    /** 已提交待复核 */
    PENDING_APPROVAL,

    /** 已批准但尚未到生效时间 */
    APPROVED,

    /** 当前生效版本 */
    ACTIVE,

    /** 复核驳回 */
    REJECTED,

    /** 被新版本替代，保留历史区间供按发生时间估算 */
    INACTIVE;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
