package com.paytech.platform.support.dataquery.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.dataquery.core.dal.dataobject.QueryTemplate;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;

/** t_query_template 的 MyBatis 映射；SQL 在 mapper/dataquery/QueryTemplateMapper.xml。 */
public interface QueryTemplateMapper {

    /** 插入新模板。 */
    int insert(@Param("row") QueryTemplate row);

    /** 按主键查询。 */
    QueryTemplate selectById(@Param("templateId") long templateId);

    /** 按编码查询。 */
    QueryTemplate selectByCode(@Param("templateCode") String templateCode);

    /** 分页查询，按 update_time、主键倒序。 */
    List<QueryTemplate> selectPage(@Param("dataSourceId") Long dataSourceId,
        @Param("status") QueryTemplateStatus status, @Param("keyword") String keyword, @Param("offset") int offset,
        @Param("size") int size);

    /** 与分页相同条件的总数。 */
    long count(@Param("dataSourceId") Long dataSourceId, @Param("status") QueryTemplateStatus status,
        @Param("keyword") String keyword);

    /** 引用某数据源（当前版本）的模板数；status 为 null 时不限状态。 */
    long countByDataSource(@Param("dataSourceId") long dataSourceId, @Param("status") QueryTemplateStatus status);

    /** 引用某数据源的模板编码，按编码排序，最多 limit 个。 */
    List<String> selectCodesByDataSource(@Param("dataSourceId") long dataSourceId,
        @Param("status") QueryTemplateStatus status, @Param("limit") int limit);

    /** 按原版本切换当前版本、冗余数据源、名称与说明，版本加一。 */
    int updateCurrentVersion(@Param("row") QueryTemplate row, @Param("next") QueryTemplate next);

    /** 按原版本与原状态更新状态，版本加一。 */
    int updateStatus(@Param("row") QueryTemplate row, @Param("status") QueryTemplateStatus status,
        @Param("updatedBy") long updatedBy, @Param("updateTime") Instant updateTime);
}
