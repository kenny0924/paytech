package com.paytech.platform.support.dataquery.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.dataquery.domain.QueryTemplateChange;
import com.paytech.platform.support.dataquery.domain.QueryTemplateDetail;
import com.paytech.platform.support.dataquery.domain.QueryTemplateVersionDetail;
import com.paytech.platform.support.dataquery.domain.SaveQueryTemplateCommand;
import com.paytech.platform.support.dataquery.domainservice.service.DataSourceService;
import com.paytech.platform.support.dataquery.domainservice.service.QueryTemplateService;
import com.paytech.platform.support.dataquery.domainservice.validator.QueryTemplateDraftValidator;
import com.paytech.platform.support.dataquery.service.processor.request.RestoreQueryTemplateRequest;
import com.paytech.platform.support.dataquery.service.processor.response.QueryTemplateSaveResult;
import com.paytech.platform.support.dataquery.template.TemplateProblem;

import lombok.AllArgsConstructor;

/**
 * 以历史版本的内容生成新版本，记录 restored_from_version_no
 * <li>按模板持资源锁；按当前规则与数据源当前状态重新校验，有错误返回 422（例如原数据源已停用）。</li>
 * <li>名称与说明沿用当前值；变更说明缺省为“恢复自版本 N”。事务与审计同修改模板。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class RestoreQueryTemplateProcessor
    extends AbstractLockProcessor<RestoreQueryTemplateRequest, QueryTemplateSaveResult> {

    private final OperationsAuditService audit;
    private final DataSourceService dataSources;
    private final QueryTemplateService templates;
    private final DataQueryManagementSupport support;
    private final QueryTemplateDraftValidator validator;

    @Override
    protected LockCommand lockCommand(RestoreQueryTemplateRequest request) {
        return LockCommand.tryLock(DataQueryManagementSupport.templateLockKey(request.getTemplateId()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected QueryTemplateSaveResult processWithinLock(RestoreQueryTemplateRequest request) {
        // 保存校验
        support.requirePermission(request.getActor(), request.getSession(),
            DataQueryManagementSupport.TEMPLATE_MANAGE_PERMISSION);
        QueryTemplateDetail current = DataQueryManagementSupport.requireFound(
            templates.findQueryTemplate(request.getTemplateId()));
        QueryTemplateVersionDetail source = templates.findQueryTemplateVersion(request.getTemplateId(),
            request.getVersionNo());
        if (source == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        List<TemplateProblem> warnings = DataQueryManagementSupport.requireValid(validator.validate(
            source.getContent(), dataSources.findDataSource(source.getDataSourceId())));
        String changeNote = CreateQueryTemplateProcessor.note(request.getChangeNote(),
            "恢复自版本 " + request.getVersionNo());

        // 本地保存与审计
        QueryTemplateChange change = support.write(request.getActor(), request.getSession(),
            DataQueryManagementSupport.TEMPLATE_MANAGE_PERMISSION, context -> {
                var now = support.now();
                QueryTemplateChange saved = templates.saveQueryTemplate(new SaveQueryTemplateCommand(
                    request.getTemplateId(), request.getExpectedVersion(), support.nextId(), current.getTemplateName(),
                    current.getDescription(), source.getContent(), request.getVersionNo(), changeNote,
                    context.account().id(), now));
                var fact = DataQueryAuditFacts.template(request.getActor(), context.member().tenantId(), saved,
                    changeNote, now);
                audit.record(() -> fact);
                return saved;
            });
        return new QueryTemplateSaveResult(change, warnings);
    }
}
