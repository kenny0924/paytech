package com.paytech.platform.support.dataquery.domainservice.service.impl;

import com.paytech.foundation.common.error.ErrorException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.dataquery.core.dal.dataobject.DataSource;
import com.paytech.platform.support.dataquery.core.dal.dataobject.QueryTemplate;
import com.paytech.platform.support.dataquery.core.dal.dataobject.QueryTemplateVersion;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.dataquery.domain.ChangeQueryTemplateStatusCommand;
import com.paytech.platform.support.dataquery.domain.CreateQueryTemplateCommand;
import com.paytech.platform.support.dataquery.domain.ExecutableQueryTemplate;
import com.paytech.platform.support.dataquery.domain.QueryTemplateChange;
import com.paytech.platform.support.dataquery.domain.QueryTemplateDetail;
import com.paytech.platform.support.dataquery.domain.QueryTemplateDraft;
import com.paytech.platform.support.dataquery.domain.QueryTemplateJson;
import com.paytech.platform.support.dataquery.domain.QueryTemplateSummary;
import com.paytech.platform.support.dataquery.domain.QueryTemplateVersionDetail;
import com.paytech.platform.support.dataquery.domain.QueryTemplateVersionSummary;
import com.paytech.platform.support.dataquery.domain.SaveQueryTemplateCommand;
import com.paytech.platform.support.dataquery.domainservice.convert.DataQueryDetailConverter;
import com.paytech.platform.support.dataquery.domainservice.repository.DataSourceRepository;
import com.paytech.platform.support.dataquery.domainservice.repository.QueryTemplateRepository;
import com.paytech.platform.support.dataquery.domainservice.repository.QueryTemplateVersionRepository;
import com.paytech.platform.support.dataquery.domainservice.service.QueryTemplateService;
import com.paytech.platform.support.dataquery.template.ParameterDeclarationReader;

/** 查询模板的本地规则与保存：版本核对、版本行与当前版本同事务切换、数据源共享锁。 */
@Service
public class QueryTemplateServiceImpl implements QueryTemplateService {

    private final DataSourceRepository dataSources;
    private final QueryTemplateRepository templates;
    private final QueryTemplateVersionRepository versions;
    private final TransactionTemplate transaction;

    /**
     * 写入使用默认传播：加入调用方事务，单独调用时自建事务，保证版本行与模板头一起提交。
     *
     * @param dataSources 数据源仓储
     * @param templates 模板仓储
     * @param versions 版本仓储
     * @param transactionManager 本库事务管理器
     */
    public QueryTemplateServiceImpl(DataSourceRepository dataSources, QueryTemplateRepository templates,
        QueryTemplateVersionRepository versions,
        @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
        this.dataSources = dataSources;
        this.templates = templates;
        this.versions = versions;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setTimeout(15);
    }

    /** 当前页一次读取当前版本与数据源，避免逐行查询。 */
    @Override
    public PagingResponseBody<QueryTemplateSummary> listQueryTemplates(Long dataSourceId, QueryTemplateStatus status,
        String keyword, PagingRequestBody paging) {
        List<QueryTemplate> rows = templates.findPage(dataSourceId, status, keyword, paging);
        Map<Long, QueryTemplateVersion> current = versions.findCurrent(rows);
        Map<Long, DataSource> sources = dataSourcesById(rows.stream().map(QueryTemplate::getDataSourceId).toList());
        List<QueryTemplateSummary> items = rows.stream().map(row -> summary(row, current.get(row.getTemplateId()),
            sources.get(row.getDataSourceId()))).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(),
            templates.count(dataSourceId, status, keyword));
    }

    /** 模板与当前版本；不存在返回 null。 */
    @Override
    public QueryTemplateDetail findQueryTemplate(long templateId) {
        QueryTemplate row = templates.findById(templateId);
        return row == null ? null : detail(row);
    }

    /** 某版本；不存在返回 null。 */
    @Override
    public QueryTemplateVersionDetail findQueryTemplateVersion(long templateId, int versionNo) {
        QueryTemplateVersion version = versions.find(templateId, versionNo);
        return version == null ? null
            : DataQueryDetailConverter.version(version, dataSources.findById(version.getDataSourceId()));
    }

    /** 版本列表附各版本的数据源编码。 */
    @Override
    public PagingResponseBody<QueryTemplateVersionSummary> listQueryTemplateVersions(long templateId,
        PagingRequestBody paging) {
        List<QueryTemplateVersion> rows = versions.findPage(templateId, paging);
        Map<Long, DataSource> sources = dataSourcesById(rows.stream().map(QueryTemplateVersion::getDataSourceId)
            .toList());
        List<QueryTemplateVersionSummary> items = rows.stream().map(row -> new QueryTemplateVersionSummary(
            row.getVersionNo(), row.getDataSourceId(), code(sources.get(row.getDataSourceId())),
            row.getRestoredFromVersionNo(), row.getChangeNote(), row.getCreatedBy(), row.getCreateTime())).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), versions.count(templateId));
    }

    /** 模板不存在返回 null；数据源记录缺失时 dataSource 为 null，由调用方拒绝。 */
    @Override
    public ExecutableQueryTemplate findExecutableTemplate(String templateCode) {
        QueryTemplate row = templates.findByCode(templateCode);
        if (row == null) {
            return null;
        }
        QueryTemplateDetail detail = detail(row);
        DataSource source = dataSources.findById(detail.getCurrentVersion().getDataSourceId());
        return new ExecutableQueryTemplate(detail, source == null ? null
            : DataQueryDetailConverter.dataSource(source, 0, 0));
    }

    /** 共享锁确认数据源 ACTIVE 后插入模板头与版本 1。 */
    @Override
    public QueryTemplateChange createQueryTemplate(CreateQueryTemplateCommand command) {
        return transaction.execute(status -> {
            QueryTemplateDraft content = command.getContent();
            DataSource source = requireActiveSource(content.getDataSourceId());
            QueryTemplate row = new QueryTemplate();
            row.setTemplateId(command.getTemplateId());
            row.setTemplateCode(command.getTemplateCode());
            row.setTemplateName(command.getTemplateName());
            row.setDescription(command.getDescription());
            row.setDataSourceId(content.getDataSourceId());
            row.setStatus(QueryTemplateStatus.ACTIVE);
            row.setCurrentVersionNo(1);
            row.setVersion(1);
            row.setCreatedBy(command.getActorAccountId());
            row.setUpdatedBy(command.getActorAccountId());
            row.setCreateTime(command.getNow());
            row.setUpdateTime(command.getNow());
            templates.insert(row);
            QueryTemplateVersion version = version(command.getTemplateVersionId(), row.getTemplateId(), 1, content,
                null, command.getChangeNote(), command.getActorAccountId(), command.getNow());
            versions.insert(version);
            return new QueryTemplateChange(null, DataQueryDetailConverter.template(row,
                DataQueryDetailConverter.version(version, source)));
        });
    }

    /** 核对版本、共享锁确认数据源 ACTIVE，再插入新版本行并切换当前版本。 */
    @Override
    public QueryTemplateChange saveQueryTemplate(SaveQueryTemplateCommand command) {
        return transaction.execute(status -> {
            QueryTemplate row = requireVersion(command.getTemplateId(), command.getExpectedVersion());
            QueryTemplateDetail before = detail(row);
            QueryTemplateDraft content = command.getContent();
            DataSource source = requireActiveSource(content.getDataSourceId());
            int versionNo = Math.addExact(row.getCurrentVersionNo(), 1);
            QueryTemplateVersion version = version(command.getTemplateVersionId(), row.getTemplateId(), versionNo,
                content, command.getRestoredFromVersionNo(), command.getChangeNote(), command.getActorAccountId(),
                command.getNow());
            versions.insert(version);
            QueryTemplate next = new QueryTemplate();
            next.setTemplateName(command.getTemplateName());
            next.setDescription(command.getDescription());
            next.setDataSourceId(content.getDataSourceId());
            next.setCurrentVersionNo(versionNo);
            next.setUpdatedBy(command.getActorAccountId());
            next.setUpdateTime(command.getNow());
            templates.switchCurrentVersion(row, next);
            return new QueryTemplateChange(before, DataQueryDetailConverter.template(row,
                DataQueryDetailConverter.version(version, source)));
        });
    }

    /** 启用时共享锁确认当前版本数据源 ACTIVE；停用不检查数据源。 */
    @Override
    public QueryTemplateChange changeQueryTemplateStatus(ChangeQueryTemplateStatusCommand command) {
        return transaction.execute(status -> {
            QueryTemplate row = requireVersion(command.getTemplateId(), command.getExpectedVersion());
            QueryTemplateDetail before = detail(row);
            if (row.getStatus() == command.getTargetStatus()) {
                return new QueryTemplateChange(before, before);
            }
            if (command.getTargetStatus() == QueryTemplateStatus.ACTIVE) {
                requireActiveSource(row.getDataSourceId());
            }
            templates.changeStatus(row, command.getTargetStatus(), command.getActorAccountId(), command.getNow());
            return new QueryTemplateChange(before, DataQueryDetailConverter.template(row, before.getCurrentVersion()));
        });
    }

    /** 模板头与当前版本；当前版本缺失视为数据不一致。 */
    QueryTemplateDetail detail(QueryTemplate row) {
        QueryTemplateVersion version = versions.find(row.getTemplateId(), row.getCurrentVersionNo());
        if (version == null) {
            throw new ErrorException("模板当前版本缺失: " + row.getTemplateCode()
                + " v" + row.getCurrentVersionNo());
        }
        return DataQueryDetailConverter.template(row,
            DataQueryDetailConverter.version(version, dataSources.findById(version.getDataSourceId())));
    }

    /** 不存在返回 404；版本不符返回 409。 */
    QueryTemplate requireVersion(long templateId, int expectedVersion) {
        QueryTemplate row = templates.findById(templateId);
        if (row == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        if (row.getVersion() != expectedVersion) {
            throw new ConcurrentRequestException("模板已被其他操作修改，请刷新后重试: " + row.getTemplateCode()
                + "，当前版本 " + row.getVersion() + "，请求版本 " + expectedVersion);
        }
        return row;
    }

    /** 共享锁读取数据源并要求 ACTIVE；锁持有到事务结束，阻止并发停用与删除。 */
    DataSource requireActiveSource(long dataSourceId) {
        DataSource source = dataSources.findByIdForShare(dataSourceId);
        if (source == null || source.getStatus() != DataSourceStatus.ACTIVE) {
            throw new BizCheckFailException("数据源不存在或未启用: " + dataSourceId);
        }
        return source;
    }

    /** 新版本行。 */
    static QueryTemplateVersion version(long id, long templateId, int versionNo, QueryTemplateDraft content,
        Integer restoredFrom, String changeNote, long actor, Instant now) {
        QueryTemplateVersion version = new QueryTemplateVersion();
        version.setTemplateVersionId(id);
        version.setTemplateId(templateId);
        version.setVersionNo(versionNo);
        version.setDataSourceId(content.getDataSourceId());
        version.setBody(content.getBody());
        version.setParametersJson(QueryTemplateJson.writeParameters(content.getParameters()));
        version.setColumnsJson(QueryTemplateJson.writeColumns(content.getColumns()));
        version.setConstraintsJson(QueryTemplateJson.writeConstraints(content.getConstraints()));
        version.setRestoredFromVersionNo(restoredFrom);
        version.setChangeNote(changeNote);
        version.setCreatedBy(actor);
        version.setCreateTime(now);
        return version;
    }

    /** 列表项：当前版本缺失时只给出模板头。 */
    QueryTemplateSummary summary(QueryTemplate row, QueryTemplateVersion version, DataSource source) {
        QueryTemplateDetail header = DataQueryDetailConverter.template(row, null);
        if (version == null) {
            return new QueryTemplateSummary(header, code(source), name(source), row.getDataSourceId(), null, List.of());
        }
        return new QueryTemplateSummary(header, code(source), name(source), row.getDataSourceId(),
            QueryTemplateJson.readConstraints(version.getConstraintsJson()).getPagingMode(),
            new ParameterDeclarationReader().read(QueryTemplateJson.readParameters(version.getParametersJson())));
    }

    /** 按 ID 批量读取数据源。 */
    Map<Long, DataSource> dataSourcesById(List<Long> ids) {
        return dataSources.findByIds(ids.stream().distinct().toList()).stream()
            .collect(Collectors.toMap(DataSource::getDataSourceId, Function.identity()));
    }

    /** 数据源编码；缺失时为 null。 */
    static String code(DataSource source) {
        return source == null ? null : source.getDataSourceCode();
    }

    /** 数据源名称；缺失时为 null。 */
    static String name(DataSource source) {
        return source == null ? null : source.getDataSourceName();
    }
}
