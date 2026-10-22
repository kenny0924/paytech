package com.paytech.platform.support.dataquery.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.dataquery.core.enums.DataSourceCheckResult;
import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;

import lombok.Getter;
import lombok.Setter;

/** t_data_source 映射对象；只在 data-query 模块内使用，不含密码原文。 */
@Getter
@Setter
public class DataSource {

    /** 数据源 Snowflake 主键。 */
    private Long dataSourceId;

    /**
     * 数据源编码
     * <li>全局唯一，创建后不可修改，删除后也不复用。</li>
     */
    private String dataSourceCode;

    /** 数据源名称。 */
    private String dataSourceName;

    /** 引擎类型。 */
    private DataSourceEngineType engineType;

    /** 主机名或 IP。 */
    private String host;

    /** 端口，1..65535。 */
    private Integer port;

    /**
     * 默认库
     * <li>为 null 表示连接整个实例。</li>
     */
    private String databaseName;

    /** 只读账号。 */
    private String username;

    /**
     * 密码 Vault REFERENCE Token
     * <li>DATA_SOURCE_PASSWORD_V1；不得写入日志或响应。</li>
     */
    private String passwordToken;

    /**
     * 驱动参数 JSON 对象原文
     * <li>只含白名单键；为 null 表示不设置。</li>
     */
    private String connectionParamsJson;

    /** 连接池最大连接数。 */
    private Integer maxPoolSize;

    /** 状态。 */
    private DataSourceStatus status;

    /** 最后一次连接检查结果；从未检查时为 null。 */
    private DataSourceCheckResult lastCheckResult;

    /** 最后一次连接检查说明；从未检查时为 null。 */
    private String lastCheckMessage;

    /** 最后一次连接检查时间（UTC）；从未检查时为 null。 */
    private Instant lastCheckTime;

    /** 说明，可为 null。 */
    private String description;

    /**
     * 乐观并发版本
     * <li>新建为 1，每次配置或状态变更加一；连接池注册表按它判断是否重建。</li>
     */
    private Integer version;

    /** 创建人账号 ID。 */
    private Long createdBy;

    /** 最后修改人账号 ID。 */
    private Long updatedBy;

    /** 创建时间（UTC）。 */
    private Instant createTime;

    /** 最后更新时间（UTC）。 */
    private Instant updateTime;
}
