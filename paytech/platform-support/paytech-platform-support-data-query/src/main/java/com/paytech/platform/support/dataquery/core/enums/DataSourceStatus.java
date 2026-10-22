package com.paytech.platform.support.dataquery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 数据源状态（t_data_source.status）：INACTIVE → ACTIVE ⇄ INACTIVE → DELETED；只有 ACTIVE 可被查询。 */
@Getter
@RequiredArgsConstructor
public enum DataSourceStatus implements CodeEnum {

    /** 已保存未启用，或已停用；不建立连接池。 */
    INACTIVE("INACTIVE"),

    /** 已通过连接检查并启用，各实例建立连接池。 */
    ACTIVE("ACTIVE"),

    /** 软删除；编码不能复用，保留行供模板历史版本显示原数据源。 */
    DELETED("DELETED");

    private final String code;
}
