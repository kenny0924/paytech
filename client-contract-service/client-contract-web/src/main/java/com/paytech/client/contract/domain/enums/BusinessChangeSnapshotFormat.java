package com.paytech.client.contract.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 业务审计存储格式的数据库闭合值域。 */
public enum BusinessChangeSnapshotFormat implements CodeEnum {
    ENTITY_NO,

    LEGACY_NUMERIC;

    @Override public String getCode() { return name(); }
}
