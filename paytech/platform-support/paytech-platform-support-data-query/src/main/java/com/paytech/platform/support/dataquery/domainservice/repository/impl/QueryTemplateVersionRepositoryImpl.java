package com.paytech.platform.support.dataquery.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.dataquery.core.dal.dataobject.QueryTemplate;
import com.paytech.platform.support.dataquery.core.dal.dataobject.QueryTemplateVersion;
import com.paytech.platform.support.dataquery.core.dal.mapper.QueryTemplateVersionMapper;
import com.paytech.platform.support.dataquery.domainservice.repository.QueryTemplateVersionRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的模板版本仓储。 */
@Repository
@AllArgsConstructor
public class QueryTemplateVersionRepositoryImpl implements QueryTemplateVersionRepository {

    private final QueryTemplateVersionMapper mapper;

    /** 版本号唯一键冲突说明另一请求已保存同一版本号，按并发冲突返回。 */
    @Override
    public void insert(QueryTemplateVersion version) {
        try {
            if (mapper.insert(version) != 1) {
                throw new ErrorException("模板版本插入行数异常: " + version.getTemplateId());
            }
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_query_template_ver")) {
                throw failure;
            }
            throw new ConcurrentRequestException("模板已被其他操作保存，请刷新后重试: " + version.getTemplateId(), failure);
        }
    }

    /** 按模板与版本号查询。 */
    @Override
    public QueryTemplateVersion find(long templateId, int versionNo) {
        return mapper.selectByTemplateAndNo(templateId, versionNo);
    }

    /** 空集合不发出 IN () 语句。 */
    @Override
    public Map<Long, QueryTemplateVersion> findCurrent(Collection<QueryTemplate> templates) {
        if (templates.isEmpty()) {
            return Map.of();
        }
        return mapper.selectCurrentVersions(templates).stream()
            .collect(Collectors.toMap(QueryTemplateVersion::getTemplateId, Function.identity()));
    }

    /** 分页查询某模板的版本。 */
    @Override
    public List<QueryTemplateVersion> findPage(long templateId, PagingRequestBody paging) {
        return mapper.selectPage(templateId, paging.offset(), paging.size());
    }

    /** 某模板的版本总数。 */
    @Override
    public long count(long templateId) {
        return mapper.count(templateId);
    }
}
