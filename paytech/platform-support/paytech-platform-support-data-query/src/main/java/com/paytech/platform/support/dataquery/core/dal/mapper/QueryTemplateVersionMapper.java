package com.paytech.platform.support.dataquery.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.dataquery.core.dal.dataobject.QueryTemplate;
import com.paytech.platform.support.dataquery.core.dal.dataobject.QueryTemplateVersion;

/** t_query_template_version 的 MyBatis 映射；只有插入与查询，版本行不修改。 */
public interface QueryTemplateVersionMapper {

    /** 插入新版本；uk_query_template_ver 防止重复版本号。 */
    int insert(@Param("row") QueryTemplateVersion row);

    /** 按模板与版本号查询。 */
    QueryTemplateVersion selectByTemplateAndNo(@Param("templateId") long templateId,
        @Param("versionNo") int versionNo);

    /** 一批模板各自的当前版本。 */
    List<QueryTemplateVersion> selectCurrentVersions(@Param("templates") Collection<QueryTemplate> templates);

    /** 同 selectCurrentVersions，加共享锁读取。 */
    List<QueryTemplateVersion> selectCurrentVersionsForShare(@Param("templates") Collection<QueryTemplate> templates);

    /** 分页查询某模板的版本，按版本号倒序。 */
    List<QueryTemplateVersion> selectPage(@Param("templateId") long templateId, @Param("offset") int offset,
        @Param("size") int size);

    /** 某模板的版本总数。 */
    long count(@Param("templateId") long templateId);
}
