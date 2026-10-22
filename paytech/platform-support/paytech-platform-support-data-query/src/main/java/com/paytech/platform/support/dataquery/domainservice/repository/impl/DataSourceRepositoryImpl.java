package com.paytech.platform.support.dataquery.domainservice.repository.impl;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.dataquery.core.dal.dataobject.DataSource;
import com.paytech.platform.support.dataquery.core.dal.mapper.DataSourceMapper;
import com.paytech.platform.support.dataquery.core.enums.DataSourceCheckResult;
import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;
import com.paytech.platform.support.dataquery.domainservice.repository.DataSourceRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的数据源仓储；条件更新行数不符时报并发冲突，成功后同步原对象。 */
@Repository
@AllArgsConstructor
public class DataSourceRepositoryImpl implements DataSourceRepository {

    private final DataSourceMapper mapper;

    /** 只把编码唯一键冲突转换为“已存在”业务拒绝，其他故障保留原异常。 */
    @Override
    public void insert(DataSource dataSource) {
        try {
            if (mapper.insert(dataSource) != 1) {
                throw new ErrorException("数据源插入行数异常: " + dataSource.getDataSourceCode());
            }
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_data_source_code")) {
                throw failure;
            }
            throw new BizCheckFailException("数据源编码已存在（含已删除）: " + dataSource.getDataSourceCode(), failure);
        }
    }

    /** 按主键查询。 */
    @Override
    public DataSource findById(long dataSourceId) {
        return mapper.selectById(dataSourceId);
    }

    /** 共享锁读取。 */
    @Override
    public DataSource findByIdForShare(long dataSourceId) {
        return mapper.selectByIdForShare(dataSourceId);
    }

    /** 空集合直接返回空列表，不发出 IN () 语句。 */
    @Override
    public List<DataSource> findByIds(Collection<Long> dataSourceIds) {
        return dataSourceIds.isEmpty() ? List.of() : mapper.selectByIds(dataSourceIds);
    }

    /** 分页查询，关键字中的 LIKE 通配符按字面匹配。 */
    @Override
    public List<DataSource> findPage(DataSourceStatus status, DataSourceEngineType engineType, String keyword,
        PagingRequestBody paging) {
        return mapper.selectPage(status, engineType, LikePatterns.escape(keyword), paging.offset(), paging.size());
    }

    /** 与分页相同条件的总数。 */
    @Override
    public long count(DataSourceStatus status, DataSourceEngineType engineType, String keyword) {
        return mapper.count(status, engineType, LikePatterns.escape(keyword));
    }

    /** 刷新比较用的轻量状态。 */
    @Override
    public List<DataSource> findRefreshStates() {
        return mapper.selectRefreshStates();
    }

    /** 条件更新成功后把目标配置写回原对象。 */
    @Override
    public void updateConfiguration(DataSource dataSource, DataSource next) {
        requireVersionAvailable(dataSource.getVersion(), dataSource.getDataSourceCode());
        requireUpdated(mapper.updateConfiguration(dataSource, next), dataSource.getDataSourceCode());
        dataSource.setDataSourceName(next.getDataSourceName());
        dataSource.setEngineType(next.getEngineType());
        dataSource.setHost(next.getHost());
        dataSource.setPort(next.getPort());
        dataSource.setDatabaseName(next.getDatabaseName());
        dataSource.setUsername(next.getUsername());
        dataSource.setPasswordToken(next.getPasswordToken());
        dataSource.setConnectionParamsJson(next.getConnectionParamsJson());
        dataSource.setMaxPoolSize(next.getMaxPoolSize());
        dataSource.setDescription(next.getDescription());
        dataSource.setVersion(dataSource.getVersion() + 1);
        dataSource.setUpdatedBy(next.getUpdatedBy());
        dataSource.setUpdateTime(next.getUpdateTime());
    }

    /** 条件更新成功后同步状态、版本与修改人。 */
    @Override
    public void changeStatus(DataSource dataSource, DataSourceStatus status, long updatedBy, Instant updateTime) {
        requireVersionAvailable(dataSource.getVersion(), dataSource.getDataSourceCode());
        requireUpdated(mapper.updateStatus(dataSource, status, updatedBy, updateTime), dataSource.getDataSourceCode());
        dataSource.setStatus(status);
        dataSource.setVersion(dataSource.getVersion() + 1);
        dataSource.setUpdatedBy(updatedBy);
        dataSource.setUpdateTime(updateTime);
    }

    /** 检查结果只是说明信息：数据源被并发删除时不写也不报错。 */
    @Override
    public void recordCheck(long dataSourceId, DataSourceCheckResult result, String message, Instant checkTime) {
        mapper.updateLastCheck(dataSourceId, result, message, checkTime);
    }

    /** 版本已到整型上限时拒绝，避免溢出。 */
    static void requireVersionAvailable(int version, String code) {
        if (version == Integer.MAX_VALUE) {
            throw new BizCheckFailException("版本已达上限，不能继续修改: " + code);
        }
    }

    /** 条件更新必须恰好命中一行。 */
    static void requireUpdated(int rows, String code) {
        if (rows != 1) {
            throw new ConcurrentRequestException("已被其他操作修改，请刷新后重试: " + code);
        }
    }
}
