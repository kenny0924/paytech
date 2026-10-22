package com.paytech.platform.support.dataquery.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.dataquery.core.dal.dataobject.QueryTemplate;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;

/** t_query_template 仓储。 */
public interface QueryTemplateRepository {

    /**
     * 插入新模板。
     *
     * @param template 已准备主键、版本与时间的新对象
     * @throws com.paytech.foundation.common.error.BizCheckFailException 编码已存在
     */
    void insert(QueryTemplate template);

    /** 按主键查询；不存在返回 null。 */
    QueryTemplate findById(long templateId);

    /** 按编码查询；不存在返回 null。 */
    QueryTemplate findByCode(String templateCode);

    /** 分页查询；筛选为 null 表示不限。 */
    List<QueryTemplate> findPage(Long dataSourceId, QueryTemplateStatus status, String keyword,
        PagingRequestBody paging);

    /** 与分页相同条件的总数。 */
    long count(Long dataSourceId, QueryTemplateStatus status, String keyword);

    /** 引用某数据源的模板数；status 为 null 时不限状态。 */
    long countByDataSource(long dataSourceId, QueryTemplateStatus status);

    /** 引用某数据源的模板编码，最多 limit 个。 */
    List<String> findCodesByDataSource(long dataSourceId, QueryTemplateStatus status, int limit);

    /**
     * 按原版本切换当前版本，成功后同步原对象。
     *
     * @param template 当前对象
     * @param next 目标：名称、说明、数据源、当前版本号、修改人与时间
     * @throws com.paytech.foundation.common.error.ConcurrentRequestException 版本已变化
     */
    void switchCurrentVersion(QueryTemplate template, QueryTemplate next);

    /** 按原版本与原状态切换状态，成功后同步原对象。 */
    void changeStatus(QueryTemplate template, QueryTemplateStatus status, long updatedBy, Instant updateTime);
}
