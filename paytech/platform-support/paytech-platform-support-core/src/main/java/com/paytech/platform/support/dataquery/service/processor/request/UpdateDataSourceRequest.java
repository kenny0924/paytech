package com.paytech.platform.support.dataquery.service.processor.request;

import com.paytech.platform.support.dataquery.domain.DataSourceConnectionConfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 修改数据源的内部请求；password 为 null 表示保留原密码。 */
@Getter
@AllArgsConstructor
public class UpdateDataSourceRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 数据源 ID。 */
    @Positive
    private final long dataSourceId;

    /** 调用方读取的原版本。 */
    @Positive
    private final int expectedVersion;

    /** 数据源名称，已去除首尾空白。 */
    @NotBlank
    @Size(max = 128)
    private final String dataSourceName;

    /** 连接参数。 */
    @NotNull
    private final DataSourceConnectionConfig connection;

    /** 新密码原文；null 表示保留原密码。 */
    private final String password;

    /** 连接池最大连接数。 */
    @Positive
    private final int maxPoolSize;

    /** 说明，可为 null。 */
    @Size(max = 512)
    private final String description;
}
