package com.paytech.client.contract.domain.enums;
import com.paytech.foundation.common.enums.CodeEnum;
/** 客户申请状态的数据库闭合集合。 */
public enum ClientApplicationStatus implements CodeEnum {
    DRAFT,

    SUBMITTED,

    UNDER_REVIEW,

    RETURNED,

    APPROVED,

    REJECTED,

    WITHDRAWN;
    @Override public String getCode() { return name(); }
}
