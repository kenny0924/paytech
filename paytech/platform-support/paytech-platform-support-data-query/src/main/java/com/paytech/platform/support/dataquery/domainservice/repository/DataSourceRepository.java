package com.paytech.platform.support.dataquery.domainservice.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.dataquery.core.dal.dataobject.DataSource;
import com.paytech.platform.support.dataquery.core.enums.DataSourceCheckResult;
import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;

/** t_data_source 仓储。 */
public interface DataSourceRepository {

    /**
     * 插入新数据源。
     *
     * @param dataSource 已准备主键、版本与时间的新对象
     * @throws com.paytech.foundation.common.error.BizCheckFailException 编码已存在（含已删除）
     */
    void insert(DataSource dataSource);

    /** 按主键查询，含 DELETED；不存在返回 null。 */
    DataSource findById(long dataSourceId);

    /** 在调用方事务内加共享锁读取，阻止并发的状态变更；不存在返回 null。 */
    DataSource findByIdForShare(long dataSourceId);

    /** 按主键批量查询，含 DELETED。 */
    List<DataSource> findByIds(Collection<Long> dataSourceIds);

    /** 分页查询未删除的数据源；筛选为 null 表示不限。 */
    List<DataSource> findPage(DataSourceStatus status, DataSourceEngineType engineType, String keyword,
        PagingRequestBody paging);

    /** 与分页相同条件的总数。 */
    long count(DataSourceStatus status, DataSourceEngineType engineType, String keyword);

    /** 未删除数据源的 ID、版本与状态，供连接池刷新比较。 */
    List<DataSource> findRefreshStates();

    /**
     * 按原版本更新连接配置，成功后同步原对象。
     *
     * @param dataSource 当前对象
     * @param next 目标配置：名称、引擎、主机、端口、默认库、账号、密码 Token、驱动参数、连接池上限、说明、修改人与时间
     * @throws com.paytech.foundation.common.error.ConcurrentRequestException 版本或状态已变化
     */
    void updateConfiguration(DataSource dataSource, DataSource next);

    /** 按原版本与原状态切换状态，成功后同步原对象。 */
    void changeStatus(DataSource dataSource, DataSourceStatus status, long updatedBy, Instant updateTime);

    /** 回写最后一次连接检查；数据源已删除时不写。 */
    void recordCheck(long dataSourceId, DataSourceCheckResult result, String message, Instant checkTime);
}
