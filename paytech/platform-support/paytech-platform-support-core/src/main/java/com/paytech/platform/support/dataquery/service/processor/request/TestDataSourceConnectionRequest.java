package com.paytech.platform.support.dataquery.service.processor.request;

import com.paytech.platform.support.dataquery.domain.DataSourceConnectionConfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 测试草稿连接配置的内部请求。 */
@Getter
@AllArgsConstructor
public class TestDataSourceConnectionRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 已保存的数据源 ID；null 表示纯草稿。 */
    private final Long dataSourceId;

    /** 连接参数。 */
    @NotNull
    private final DataSourceConnectionConfig connection;

    /** 密码原文；null 表示使用已保存的密码（必须带 dataSourceId）。 */
    private final String password;
}
