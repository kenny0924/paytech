package com.paytech.platform.support.dataquery.domainservice.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.dataquery.core.dal.dataobject.QueryTemplate;
import com.paytech.platform.support.dataquery.core.dal.dataobject.QueryTemplateVersion;

/** t_query_template_version 仓储；版本行只插入不修改。 */
public interface QueryTemplateVersionRepository {

    /**
     * 插入新版本。
     *
     * @param version 新版本
     * @throws com.paytech.foundation.common.error.ConcurrentRequestException 同模板同版本号已存在（并发保存）
     */
    void insert(QueryTemplateVersion version);

    /** 按模板与版本号查询；不存在返回 null。 */
    QueryTemplateVersion find(long templateId, int versionNo);

    /** 一批模板各自的当前版本，键为模板 ID。 */
    Map<Long, QueryTemplateVersion> findCurrent(Collection<QueryTemplate> templates);

    /** 分页查询某模板的版本，按版本号倒序。 */
    List<QueryTemplateVersion> findPage(long templateId, PagingRequestBody paging);

    /** 某模板的版本总数。 */
    long count(long templateId);
}
