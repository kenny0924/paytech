package com.paytech.platform.support.dataquery.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.dataquery.core.dal.dataobject.QueryTemplate;
import com.paytech.platform.support.dataquery.core.dal.mapper.QueryTemplateMapper;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.dataquery.domainservice.repository.QueryTemplateRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的模板仓储；条件更新行数不符时报并发冲突，成功后同步原对象。 */
@Repository
@AllArgsConstructor
public class QueryTemplateRepositoryImpl implements QueryTemplateRepository {

    private final QueryTemplateMapper mapper;

    /** 只把编码唯一键冲突转换为“已存在”业务拒绝。 */
    @Override
    public void insert(QueryTemplate template) {
        try {
            if (mapper.insert(template) != 1) {
                throw new ErrorException("模板插入行数异常: " + template.getTemplateCode());
            }
        } catch (DuplicateKeyException failure) {
            if (!String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uk_query_template_code")) {
                throw failure;
            }
            throw new BizCheckFailException("模板编码已存在: " + template.getTemplateCode(), failure);
        }
    }

    /** 按主键查询。 */
    @Override
    public QueryTemplate findById(long templateId) {
        return mapper.selectById(templateId);
    }

    /** 按编码查询。 */
    @Override
    public QueryTemplate findByCode(String templateCode) {
        return mapper.selectByCode(templateCode);
    }

    /** 分页查询，关键字按字面匹配。 */
    @Override
    public List<QueryTemplate> findPage(Long dataSourceId, QueryTemplateStatus status, String keyword,
        PagingRequestBody paging) {
        return mapper.selectPage(dataSourceId, status, LikePatterns.escape(keyword), paging.offset(), paging.size());
    }

    /** 全部模板，按编码升序。 */
    @Override
    public List<QueryTemplate> findAll(QueryTemplateStatus status) {
        return mapper.selectAll(status, MAX_ALL_TEMPLATES);
    }

    /** 加共享锁读取全部模板。 */
    @Override
    public List<QueryTemplate> findAllForShare(QueryTemplateStatus status) {
        return mapper.selectAllForShare(status, MAX_ALL_TEMPLATES);
    }

    /** 与分页相同条件的总数。 */
    @Override
    public long count(Long dataSourceId, QueryTemplateStatus status, String keyword) {
        return mapper.count(dataSourceId, status, LikePatterns.escape(keyword));
    }

    /** 引用某数据源的模板数。 */
    @Override
    public long countByDataSource(long dataSourceId, QueryTemplateStatus status) {
        return mapper.countByDataSource(dataSourceId, status);
    }

    /** 引用某数据源的模板编码。 */
    @Override
    public List<String> findCodesByDataSource(long dataSourceId, QueryTemplateStatus status, int limit) {
        return mapper.selectCodesByDataSource(dataSourceId, status, limit);
    }

    /** 条件更新成功后同步名称、说明、数据源、当前版本、版本与修改人。 */
    @Override
    public void switchCurrentVersion(QueryTemplate template, QueryTemplate next) {
        DataSourceRepositoryImpl.requireVersionAvailable(template.getVersion(), template.getTemplateCode());
        DataSourceRepositoryImpl.requireUpdated(mapper.updateCurrentVersion(template, next),
            template.getTemplateCode());
        template.setTemplateName(next.getTemplateName());
        template.setDescription(next.getDescription());
        template.setDataSourceId(next.getDataSourceId());
        template.setCurrentVersionNo(next.getCurrentVersionNo());
        template.setVersion(template.getVersion() + 1);
        template.setUpdatedBy(next.getUpdatedBy());
        template.setUpdateTime(next.getUpdateTime());
    }

    /** 条件更新成功后同步状态、版本与修改人。 */
    @Override
    public void changeStatus(QueryTemplate template, QueryTemplateStatus status, long updatedBy, Instant updateTime) {
        DataSourceRepositoryImpl.requireVersionAvailable(template.getVersion(), template.getTemplateCode());
        DataSourceRepositoryImpl.requireUpdated(mapper.updateStatus(template, status, updatedBy, updateTime),
            template.getTemplateCode());
        template.setStatus(status);
        template.setVersion(template.getVersion() + 1);
        template.setUpdatedBy(updatedBy);
        template.setUpdateTime(updateTime);
    }
}
