package com.paytech.platform.support.dataquery.domainservice.service;

import java.time.Instant;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;
import com.paytech.platform.support.dataquery.domain.ChangeDataSourceStatusCommand;
import com.paytech.platform.support.dataquery.domain.ConnectionCheckResult;
import com.paytech.platform.support.dataquery.domain.CreateDataSourceCommand;
import com.paytech.platform.support.dataquery.domain.DataSourceChange;
import com.paytech.platform.support.dataquery.domain.DataSourceDetail;
import com.paytech.platform.support.dataquery.domain.UpdateDataSourceCommand;

/**
 * 数据源的本地查询与保存；不访问 Vault 或被查库。
 * <li>写方法须在调用方的本地事务内调用，与权限复核和审计一起提交。</li>
 */
public interface DataSourceService {

    /** 分页查询未删除的数据源，附引用模板数。 */
    PagingResponseBody<DataSourceDetail> listDataSources(DataSourceStatus status, DataSourceEngineType engineType,
        String keyword, PagingRequestBody paging);

    /**
     * 数据源事实，附引用模板数。
     *
     * @param dataSourceId 数据源 ID
     * @return 事实；不存在时为 null
     */
    DataSourceDetail findDataSource(long dataSourceId);

    /** 新建 INACTIVE 数据源，版本为 1。 */
    DataSourceDetail createDataSource(CreateDataSourceCommand command);

    /**
     * 按原版本修改配置；与当前完全相同时不写入、不递增版本。
     *
     * @param command 修改命令
     * @return 前后事实
     * @throws com.paytech.foundation.common.error.ConcurrentRequestException 版本不符
     */
    DataSourceChange updateDataSource(UpdateDataSourceCommand command);

    /**
     * 按原版本切换状态；目标与当前相同时不写入。
     * <li>DELETED 只允许从 INACTIVE 进入，且没有 ACTIVE 模板引用；DELETED 不能再变更。</li>
     *
     * @param command 状态命令
     * @return 前后事实
     */
    DataSourceChange changeDataSourceStatus(ChangeDataSourceStatusCommand command);

    /** 回写最后一次连接检查，不改变版本；数据源已删除时不写。 */
    void recordConnectionCheck(long dataSourceId, ConnectionCheckResult result, Instant checkTime);
}
