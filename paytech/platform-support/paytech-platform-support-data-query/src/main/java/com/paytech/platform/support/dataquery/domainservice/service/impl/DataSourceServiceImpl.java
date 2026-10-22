package com.paytech.platform.support.dataquery.domainservice.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.dataquery.core.dal.dataobject.DataSource;
import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.dataquery.domain.ChangeDataSourceStatusCommand;
import com.paytech.platform.support.dataquery.domain.ConnectionCheckResult;
import com.paytech.platform.support.dataquery.domain.CreateDataSourceCommand;
import com.paytech.platform.support.dataquery.domain.DataSourceChange;
import com.paytech.platform.support.dataquery.domain.DataSourceConnectionConfig;
import com.paytech.platform.support.dataquery.domain.DataSourceDetail;
import com.paytech.platform.support.dataquery.domain.QueryTemplateJson;
import com.paytech.platform.support.dataquery.domain.UpdateDataSourceCommand;
import com.paytech.platform.support.dataquery.domainservice.convert.DataQueryDetailConverter;
import com.paytech.platform.support.dataquery.domainservice.repository.DataSourceRepository;
import com.paytech.platform.support.dataquery.domainservice.repository.QueryTemplateRepository;
import com.paytech.platform.support.dataquery.domainservice.service.DataSourceService;

import lombok.AllArgsConstructor;

/** 数据源的本地规则与保存：版本核对、状态迁移与删除前的引用检查。 */
@Service
@AllArgsConstructor
public class DataSourceServiceImpl implements DataSourceService {

    /** 删除被拒绝时最多列出的引用模板编码数。 */
    private static final int MAX_LISTED_TEMPLATES = 10;

    private final DataSourceRepository dataSources;
    private final QueryTemplateRepository templates;

    /** 当前页逐个统计引用模板数（每页最多 200 条）。 */
    @Override
    public PagingResponseBody<DataSourceDetail> listDataSources(DataSourceStatus status,
        DataSourceEngineType engineType, String keyword, PagingRequestBody paging) {
        List<DataSourceDetail> items = dataSources.findPage(status, engineType, keyword, paging).stream()
            .map(this::withCounts).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(),
            dataSources.count(status, engineType, keyword));
    }

    /** 不存在返回 null。 */
    @Override
    public DataSourceDetail findDataSource(long dataSourceId) {
        DataSource row = dataSources.findById(dataSourceId);
        return row == null ? null : withCounts(row);
    }

    /** 插入新行；编码唯一键为最终保护。 */
    @Override
    public DataSourceDetail createDataSource(CreateDataSourceCommand command) {
        DataSource row = new DataSource();
        row.setDataSourceId(command.getDataSourceId());
        row.setDataSourceCode(command.getDataSourceCode());
        row.setStatus(DataSourceStatus.INACTIVE);
        row.setVersion(1);
        row.setCreatedBy(command.getActorAccountId());
        row.setCreateTime(command.getNow());
        applyConfiguration(row, command.getDataSourceName(), command.getConnection(), command.getPasswordToken(),
            command.getMaxPoolSize(), command.getDescription());
        row.setUpdatedBy(command.getActorAccountId());
        row.setUpdateTime(command.getNow());
        dataSources.insert(row);
        return DataQueryDetailConverter.dataSource(row, 0, 0);
    }

    /** 读取并核对版本后条件更新；完全相同时原样返回。 */
    @Override
    public DataSourceChange updateDataSource(UpdateDataSourceCommand command) {
        DataSource row = requireVersion(command.getDataSourceId(), command.getExpectedVersion());
        requireNotDeleted(row);
        DataSourceDetail before = DataQueryDetailConverter.dataSource(row, 0, 0);
        DataSource next = new DataSource();
        applyConfiguration(next, command.getDataSourceName(), command.getConnection(),
            command.getPasswordToken() == null ? row.getPasswordToken() : command.getPasswordToken(),
            command.getMaxPoolSize(), command.getDescription());
        if (sameConfiguration(row, next)) {
            return new DataSourceChange(before, before);
        }
        next.setUpdatedBy(command.getActorAccountId());
        next.setUpdateTime(command.getNow());
        dataSources.updateConfiguration(row, next);
        return new DataSourceChange(before, DataQueryDetailConverter.dataSource(row, 0, 0));
    }

    /** 核对版本与迁移规则后条件更新；删除前检查 ACTIVE 模板引用。 */
    @Override
    public DataSourceChange changeDataSourceStatus(ChangeDataSourceStatusCommand command) {
        DataSource row = requireVersion(command.getDataSourceId(), command.getExpectedVersion());
        requireNotDeleted(row);
        DataSourceDetail before = DataQueryDetailConverter.dataSource(row, 0, 0);
        if (row.getStatus() == command.getTargetStatus()) {
            return new DataSourceChange(before, before);
        }
        if (command.getTargetStatus() == DataSourceStatus.DELETED) {
            requireInactive(row);
        }
        dataSources.changeStatus(row, command.getTargetStatus(), command.getActorAccountId(), command.getNow());
        if (command.getTargetStatus() == DataSourceStatus.DELETED) {
            // 行锁取得后再读引用：模板写入对数据源持共享锁，已提交的启用在此可见；有引用时整个事务回滚。
            requireNoActiveTemplates(row);
        }
        return new DataSourceChange(before, DataQueryDetailConverter.dataSource(row, 0, 0));
    }

    /** 只回写检查结果。 */
    @Override
    public void recordConnectionCheck(long dataSourceId, ConnectionCheckResult result, Instant checkTime) {
        dataSources.recordCheck(dataSourceId, result.result(), result.message(), checkTime);
    }

    /** 附引用模板数。 */
    DataSourceDetail withCounts(DataSource row) {
        return DataQueryDetailConverter.dataSource(row, templates.countByDataSource(row.getDataSourceId(), null),
            templates.countByDataSource(row.getDataSourceId(), QueryTemplateStatus.ACTIVE));
    }

    /** 不存在返回 404；版本不符返回 409。 */
    DataSource requireVersion(long dataSourceId, int expectedVersion) {
        DataSource row = dataSources.findById(dataSourceId);
        if (row == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        if (row.getVersion() != expectedVersion) {
            throw new ConcurrentRequestException("数据源已被其他操作修改，请刷新后重试: " + row.getDataSourceCode()
                + "，当前版本 " + row.getVersion() + "，请求版本 " + expectedVersion);
        }
        return row;
    }

    /** 已删除的数据源不能再修改。 */
    void requireNotDeleted(DataSource row) {
        if (row.getStatus() == DataSourceStatus.DELETED) {
            throw new BizCheckFailException("数据源已删除，不能修改: " + row.getDataSourceCode());
        }
    }

    /** 删除要求当前为 INACTIVE。 */
    void requireInactive(DataSource row) {
        if (row.getStatus() != DataSourceStatus.INACTIVE) {
            throw new BizCheckFailException("只能删除已停用的数据源: " + row.getDataSourceCode() + "，当前状态 "
                + row.getStatus().getCode());
        }
    }

    /** 删除要求没有 ACTIVE 模板引用，消息列出最多 10 个模板编码。 */
    void requireNoActiveTemplates(DataSource row) {
        List<String> active = templates.findCodesByDataSource(row.getDataSourceId(), QueryTemplateStatus.ACTIVE,
            MAX_LISTED_TEMPLATES);
        if (!active.isEmpty()) {
            throw new BizCheckFailException("数据源仍被启用的模板引用，不能删除: " + row.getDataSourceCode() + "，模板 "
                + String.join("、", active));
        }
    }

    /** 写入配置字段（含驱动参数 JSON）。 */
    static void applyConfiguration(DataSource row, String name, DataSourceConnectionConfig connection,
        String passwordToken, int maxPoolSize, String description) {
        row.setDataSourceName(name);
        row.setEngineType(connection.getEngineType());
        row.setHost(connection.getHost());
        row.setPort(connection.getPort());
        row.setDatabaseName(connection.getDatabaseName());
        row.setUsername(connection.getUsername());
        row.setPasswordToken(passwordToken);
        row.setConnectionParamsJson(QueryTemplateJson.writeConnectionParams(connection.getConnectionParams()));
        row.setMaxPoolSize(maxPoolSize);
        row.setDescription(description);
    }

    /** 配置字段是否完全相同；驱动参数按解析后的键值比较。 */
    static boolean sameConfiguration(DataSource row, DataSource next) {
        return Objects.equals(row.getDataSourceName(), next.getDataSourceName())
            && row.getEngineType() == next.getEngineType() && Objects.equals(row.getHost(), next.getHost())
            && Objects.equals(row.getPort(), next.getPort())
            && Objects.equals(row.getDatabaseName(), next.getDatabaseName())
            && Objects.equals(row.getUsername(), next.getUsername())
            && Objects.equals(row.getPasswordToken(), next.getPasswordToken())
            && QueryTemplateJson.readConnectionParams(row.getConnectionParamsJson())
                .equals(QueryTemplateJson.readConnectionParams(next.getConnectionParamsJson()))
            && Objects.equals(row.getMaxPoolSize(), next.getMaxPoolSize())
            && Objects.equals(row.getDescription(), next.getDescription());
    }
}
