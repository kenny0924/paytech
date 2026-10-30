package com.paytech.platform.support.dataquery.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.dataquery.domain.DataSourceDetail;
import com.paytech.platform.support.dataquery.domain.QueryTemplateChange;
import com.paytech.platform.support.dataquery.domain.QueryTemplateDetail;
import com.paytech.platform.support.dataquery.domain.SaveQueryTemplateCommand;
import com.paytech.platform.support.dataquery.domainservice.service.DataSourceService;
import com.paytech.platform.support.dataquery.domainservice.service.QueryTemplateService;
import com.paytech.platform.support.dataquery.domainservice.validator.QueryTemplateDraftValidator;
import com.paytech.platform.support.dataquery.service.processor.request.UpdateQueryTemplateRequest;
import com.paytech.platform.support.dataquery.service.processor.response.QueryTemplateSaveResult;
import com.paytech.platform.support.dataquery.template.TemplateProblem;
import com.paytech.platform.support.dataquery.template.TemplateValidationResult;

import lombok.AllArgsConstructor;

/**
 * 修改模板：保存为新版本并立即生效（不复核）
 * <li>按模板持资源锁；事务前确认权限、模板存在并做保存校验，有错误返回 422 且不写库。</li>
 * <li>本地事务内复核权限、核对原版本、对数据源与引用的键类型加共享锁确认 ACTIVE、插入新版本行并切换当前版本与冗余数据源、
 * 版本加一、登记审计。联合查询参与提示按模板当前状态推算。</li>
 * <li>内容未变也生成新版本，保存记录即版本历史。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateQueryTemplateProcessor
    extends AbstractLockProcessor<UpdateQueryTemplateRequest, QueryTemplateSaveResult> {

    private final OperationsAuditService audit;
    private final DataSourceService dataSources;
    private final QueryTemplateService templates;
    private final DataQueryManagementSupport support;
    private final QueryTemplateDraftValidator validator;

    @Override
    protected LockCommand lockCommand(UpdateQueryTemplateRequest request) {
        return LockCommand.tryLock(DataQueryManagementSupport.templateLockKey(request.getTemplateId()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected QueryTemplateSaveResult processWithinLock(UpdateQueryTemplateRequest request) {
        // 保存校验
        support.requirePermission(request.getActor(), request.getSession(),
            DataQueryManagementSupport.TEMPLATE_MANAGE_PERMISSION);
        QueryTemplateDetail current = DataQueryManagementSupport.requireFound(
            templates.findQueryTemplate(request.getTemplateId()));
        DataSourceDetail dataSource = dataSources.findDataSource(request.getDraft().getDataSourceId());
        TemplateValidationResult result = validator.validate(request.getDraft(), dataSource,
            support.referencedKeyTypes(request.getDraft()));
        List<TemplateProblem> warnings = DataQueryManagementSupport.withParticipation(
            DataQueryManagementSupport.requireValid(result), result, request.getDraft(), dataSource,
            current.getStatus() == QueryTemplateStatus.ACTIVE);

        // 本地保存与审计
        QueryTemplateChange change = support.write(request.getActor(), request.getSession(),
            DataQueryManagementSupport.TEMPLATE_MANAGE_PERMISSION, context -> {
                var now = support.now();
                QueryTemplateChange saved = templates.saveQueryTemplate(new SaveQueryTemplateCommand(
                    request.getTemplateId(), request.getExpectedVersion(), support.nextId(), request.getTemplateName(),
                    request.getDescription(), request.getDraft(), null, request.getChangeNote(),
                    context.account().id(), now));
                var fact = DataQueryAuditFacts.template(request.getActor(), context.member().tenantId(), saved,
                    CreateQueryTemplateProcessor.note(request.getChangeNote(), "修改查询模板"), now);
                audit.record(() -> fact);
                return saved;
            });
        return new QueryTemplateSaveResult(change, warnings);
    }
}
