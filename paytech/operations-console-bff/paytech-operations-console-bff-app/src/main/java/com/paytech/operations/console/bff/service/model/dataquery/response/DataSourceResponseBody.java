package com.paytech.operations.console.bff.service.model.dataquery.response;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/** 一个数据源的配置与最后一次检查结果；不含密码或 Vault Token。列表与详情共用。 */
@Getter
@Setter
public class DataSourceResponseBody {

    /** 数据源 ID，十进制字符串。 */
    private String dataSourceId;

    /** 数据源编码，创建后不可修改。 */
    private String dataSourceCode;

    /** 数据源名称。 */
    private String dataSourceName;

    /** 引擎类型：MYSQL、DORIS。 */
    private String engineType;

    /** 主机名或 IP。 */
    private String host;

    /** 端口。 */
    private int port;

    /**
     * 默认库
     * <li>为 null 表示连接整个实例，页面显示“整个实例”。</li>
     */
    private String databaseName;

    /** 只读账号。 */
    private String username;

    /**
     * 驱动参数
     * <li>只含白名单五项；没有时为空对象。</li>
     */
    private Map<String, String> connectionParams;

    /** 连接池最大连接数。 */
    private int maxPoolSize;

    /** 状态：INACTIVE、ACTIVE、DELETED。 */
    private String status;

    /**
     * 最后一次检查结果
     * <li>PASSED、FAILED；从未检查时为 null。</li>
     */
    private String lastCheckResult;

    /** 最后一次检查的说明；未通过时列出具体原因，从未检查时为 null。 */
    private String lastCheckMessage;

    /** 最后一次检查时间，UTC ISO-8601 微秒精度；从未检查时为 null。 */
    private String lastCheckTime;

    /** 说明，可为 null。 */
    private String description;

    /** 引用该数据源（当前版本）的模板数，含 INACTIVE 模板。 */
    private long templateCount;

    /** 其中 ACTIVE 模板数；大于 0 时不能删除。 */
    private long activeTemplateCount;

    /** 乐观并发版本，写接口按此传 expectedVersion。 */
    private int version;

    /** 创建人账号 ID，十进制字符串。 */
    private String createdBy;

    /** 最后修改人账号 ID，十进制字符串。 */
    private String updatedBy;

    /** 创建时间，UTC ISO-8601 微秒精度。 */
    private String createTime;

    /** 最后修改时间，UTC ISO-8601 微秒精度。 */
    private String updateTime;
}
