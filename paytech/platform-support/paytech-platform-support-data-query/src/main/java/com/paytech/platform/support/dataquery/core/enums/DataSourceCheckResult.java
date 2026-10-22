package com.paytech.platform.support.dataquery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 数据源最后一次连接检查结果（t_data_source.last_check_result）。 */
@Getter
@RequiredArgsConstructor
public enum DataSourceCheckResult implements CodeEnum {

    /** 连通且账号只有只读权限。 */
    PASSED("PASSED"),

    /** 连接失败或账号权限不符合只读要求。 */
    FAILED("FAILED");

    private final String code;
}
