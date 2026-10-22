package com.paytech.platform.support.dataquery.domainservice.convert;

import com.paytech.platform.support.dataquery.core.dal.dataobject.DataSource;
import com.paytech.platform.support.dataquery.core.dal.dataobject.QueryTemplate;
import com.paytech.platform.support.dataquery.core.dal.dataobject.QueryTemplateVersion;
import com.paytech.platform.support.dataquery.domain.DataSourceConnectionConfig;
import com.paytech.platform.support.dataquery.domain.DataSourceDetail;
import com.paytech.platform.support.dataquery.domain.QueryTemplateDetail;
import com.paytech.platform.support.dataquery.domain.QueryTemplateDraft;
import com.paytech.platform.support.dataquery.domain.QueryTemplateJson;
import com.paytech.platform.support.dataquery.domain.QueryTemplateVersionDetail;

/** 本模块 DataObject 到对外发布的领域事实的转换；不访问数据库。 */
public final class DataQueryDetailConverter {

    private DataQueryDetailConverter() {
    }

    /**
     * 数据源事实。
     *
     * @param row 数据源行
     * @param templateCount 引用模板数
     * @param activeTemplateCount 其中 ACTIVE 模板数
     * @return 领域事实
     */
    public static DataSourceDetail dataSource(DataSource row, long templateCount, long activeTemplateCount) {
        return DataSourceDetail.builder().dataSourceId(row.getDataSourceId()).dataSourceCode(row.getDataSourceCode())
            .dataSourceName(row.getDataSourceName()).connection(connection(row)).passwordToken(row.getPasswordToken())
            .maxPoolSize(row.getMaxPoolSize()).status(row.getStatus()).lastCheckResult(row.getLastCheckResult())
            .lastCheckMessage(row.getLastCheckMessage()).lastCheckTime(row.getLastCheckTime())
            .description(row.getDescription()).version(row.getVersion()).createdBy(row.getCreatedBy())
            .updatedBy(row.getUpdatedBy()).createTime(row.getCreateTime()).updateTime(row.getUpdateTime())
            .templateCount(templateCount).activeTemplateCount(activeTemplateCount).build();
    }

    /**
     * 行中的连接参数。
     *
     * @param row 数据源行
     * @return 连接参数
     */
    public static DataSourceConnectionConfig connection(DataSource row) {
        return new DataSourceConnectionConfig(row.getEngineType(), row.getHost(), row.getPort(), row.getDatabaseName(),
            row.getUsername(), QueryTemplateJson.readConnectionParams(row.getConnectionParamsJson()));
    }

    /**
     * 版本事实。
     *
     * @param version 版本行
     * @param dataSource 该版本的数据源行；缺失时为 null
     * @return 领域事实
     */
    public static QueryTemplateVersionDetail version(QueryTemplateVersion version, DataSource dataSource) {
        return QueryTemplateVersionDetail.builder().templateId(version.getTemplateId())
            .versionNo(version.getVersionNo()).dataSourceId(version.getDataSourceId())
            .dataSourceCode(dataSource == null ? null : dataSource.getDataSourceCode())
            .dataSourceName(dataSource == null ? null : dataSource.getDataSourceName())
            .dataSourceStatus(dataSource == null ? null : dataSource.getStatus())
            .content(new QueryTemplateDraft(version.getDataSourceId(), version.getBody(),
                QueryTemplateJson.readParameters(version.getParametersJson()),
                QueryTemplateJson.readColumns(version.getColumnsJson()),
                QueryTemplateJson.readConstraints(version.getConstraintsJson())))
            .restoredFromVersionNo(version.getRestoredFromVersionNo()).changeNote(version.getChangeNote())
            .createdBy(version.getCreatedBy()).createTime(version.getCreateTime()).build();
    }

    /**
     * 模板事实。
     *
     * @param template 模板行
     * @param currentVersion 当前版本事实；只需要模板头时为 null
     * @return 领域事实
     */
    public static QueryTemplateDetail template(QueryTemplate template, QueryTemplateVersionDetail currentVersion) {
        return QueryTemplateDetail.builder().templateId(template.getTemplateId())
            .templateCode(template.getTemplateCode()).templateName(template.getTemplateName())
            .description(template.getDescription()).status(template.getStatus())
            .currentVersionNo(template.getCurrentVersionNo()).version(template.getVersion())
            .createdBy(template.getCreatedBy()).updatedBy(template.getUpdatedBy())
            .createTime(template.getCreateTime()).updateTime(template.getUpdateTime()).currentVersion(currentVersion)
            .build();
    }
}
