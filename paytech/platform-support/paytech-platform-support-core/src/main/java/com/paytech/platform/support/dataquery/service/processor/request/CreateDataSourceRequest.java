package com.paytech.platform.support.dataquery.service.processor.request;

import com.paytech.platform.support.dataquery.domain.DataSourceConnectionConfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建数据源的内部请求；密码只在本请求内存在，Processor 在事务外保存到 Vault。 */
@Getter
@AllArgsConstructor
public class CreateDataSourceRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 数据源编码。 */
    @NotBlank
    private final String dataSourceCode;

    /** 数据源名称，已去除首尾空白。 */
    @NotBlank
    @Size(max = 128)
    private final String dataSourceName;

    /** 连接参数，驱动参数已按白名单校验。 */
    @NotNull
    private final DataSourceConnectionConfig connection;

    /** 密码原文。 */
    @NotBlank
    private final String password;

    /** 连接池最大连接数。 */
    @Positive
    private final int maxPoolSize;

    /** 说明，可为 null。 */
    @Size(max = 512)
    private final String description;
}
