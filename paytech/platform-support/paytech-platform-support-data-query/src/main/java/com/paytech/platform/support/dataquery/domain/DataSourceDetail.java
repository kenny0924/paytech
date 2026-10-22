package com.paytech.platform.support.dataquery.domain;

import java.time.Instant;

import com.paytech.platform.support.dataquery.core.enums.DataSourceCheckResult;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;

import lombok.Builder;
import lombok.Getter;

/**
 * data-query 对外发布的数据源事实；passwordToken 只供 Vault Reveal，不得进入 HTTP 响应或日志。
 */
@Getter
@Builder(toBuilder = true)
public final class DataSourceDetail {

    /** 数据源 ID。 */
    private final long dataSourceId;

    /** 数据源编码。 */
    private final String dataSourceCode;

    /** 数据源名称。 */
    private final String dataSourceName;

    /** 连接参数。 */
    private final DataSourceConnectionConfig connection;

    /** 密码 Vault REFERENCE Token。 */
    private final String passwordToken;

    /** 连接池最大连接数。 */
    private final int maxPoolSize;

    /** 状态。 */
    private final DataSourceStatus status;

    /** 最后一次检查结果；从未检查时为 null。 */
    private final DataSourceCheckResult lastCheckResult;

    /** 最后一次检查说明；从未检查时为 null。 */
    private final String lastCheckMessage;

    /** 最后一次检查时间；从未检查时为 null。 */
    private final Instant lastCheckTime;

    /** 说明。 */
    private final String description;

    /** 乐观并发版本。 */
    private final int version;

    /** 创建人账号 ID。 */
    private final long createdBy;

    /** 最后修改人账号 ID。 */
    private final long updatedBy;

    /** 创建时间。 */
    private final Instant createTime;

    /** 最后修改时间。 */
    private final Instant updateTime;

    /** 引用该数据源（当前版本）的模板数；只在查询接口填写，写接口结果为 0。 */
    private final long templateCount;

    /** 其中 ACTIVE 模板数；只在查询接口填写。 */
    private final long activeTemplateCount;

    /**
     * 是否处于某状态。
     *
     * @param expected 期望状态
     * @return 相同为 true
     */
    public boolean statusIs(DataSourceStatus expected) {
        return status == expected;
    }
}
