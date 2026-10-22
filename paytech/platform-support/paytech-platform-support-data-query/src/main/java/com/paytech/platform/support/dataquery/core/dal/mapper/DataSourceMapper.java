package com.paytech.platform.support.dataquery.core.dal.mapper;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.dataquery.core.dal.dataobject.DataSource;
import com.paytech.platform.support.dataquery.core.enums.DataSourceCheckResult;
import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;

/** t_data_source 的 MyBatis 映射；SQL 在 mapper/dataquery/DataSourceMapper.xml。 */
public interface DataSourceMapper {

    /** 插入新数据源。 */
    int insert(@Param("row") DataSource row);

    /** 按主键查询，含 DELETED。 */
    DataSource selectById(@Param("dataSourceId") long dataSourceId);

    /** 按主键加共享锁读取，阻止并发的状态变更直到本事务结束。 */
    DataSource selectByIdForShare(@Param("dataSourceId") long dataSourceId);

    /** 按主键批量查询，含 DELETED。 */
    List<DataSource> selectByIds(@Param("ids") Collection<Long> ids);

    /** 分页查询未删除的数据源，按 update_time、主键倒序。 */
    List<DataSource> selectPage(@Param("status") DataSourceStatus status,
        @Param("engineType") DataSourceEngineType engineType, @Param("keyword") String keyword,
        @Param("offset") int offset, @Param("size") int size);

    /** 与分页相同条件的总数。 */
    long count(@Param("status") DataSourceStatus status, @Param("engineType") DataSourceEngineType engineType,
        @Param("keyword") String keyword);

    /** 刷新任务的轻量查询：未删除数据源的 ID、版本与状态。 */
    List<DataSource> selectRefreshStates();

    /** 按原版本更新连接配置、名称与说明，版本加一。 */
    int updateConfiguration(@Param("row") DataSource row, @Param("next") DataSource next);

    /** 按原版本与原状态更新状态，版本加一。 */
    int updateStatus(@Param("row") DataSource row, @Param("status") DataSourceStatus status,
        @Param("updatedBy") long updatedBy, @Param("updateTime") Instant updateTime);

    /** 回写最后一次连接检查；不改变版本，不触发连接池重建。 */
    int updateLastCheck(@Param("dataSourceId") long dataSourceId, @Param("result") DataSourceCheckResult result,
        @Param("message") String message, @Param("checkTime") Instant checkTime);
}
