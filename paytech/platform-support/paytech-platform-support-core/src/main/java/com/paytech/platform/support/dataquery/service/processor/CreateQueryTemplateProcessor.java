package com.paytech.platform.support.dataquery.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.dataquery.domain.CreateQueryTemplateCommand;
import com.paytech.platform.support.dataquery.domain.DataSourceDetail;
import com.paytech.platform.support.dataquery.domain.QueryTemplateChange;
import com.paytech.platform.support.dataquery.domainservice.service.DataSourceService;
import com.paytech.platform.support.dataquery.domainservice.service.QueryTemplateService;
import com.paytech.platform.support.dataquery.domainservice.validator.QueryTemplateDraftValidator;
import com.paytech.platform.support.dataquery.service.processor.request.CreateQueryTemplateRequest;
import com.paytech.platform.support.dataquery.service.processor.response.QueryTemplateSaveResult;
import com.paytech.platform.support.dataquery.template.TemplateProblem;
import com.paytech.platform.support.dataquery.template.TemplateValidationResult;

import lombok.AllArgsConstructor;

/**
 * 新建模板，保存为版本 1，状态 ACTIVE，立即可执行
 * <li>按编码持资源锁；事务前确认 DATA_QUERY_TEMPLATE_MANAGE 并做保存校验（B2，含联合查询配置），有错误返回 422 且不写库，
 * 警告与联合查询参与提示随结果返回。</li>
 * <li>本地事务内复核权限、对数据源与引用的键类型加共享锁确认 ACTIVE、插入模板与版本 1 并登记审计；编码已存在返回 400。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateQueryTemplateProcessor
    extends AbstractLockProcessor<CreateQueryTemplateRequest, QueryTemplateSaveResult> {

    private final OperationsAuditService audit;
    private final DataSourceService dataSources;
    private final QueryTemplateService templates;
    private final DataQueryManagementSupport support;
    private final QueryTemplateDraftValidator validator;

    @Override
    protected LockCommand lockCommand(CreateQueryTemplateRequest request) {
        return LockCommand.tryLock(DataQueryManagementSupport.templateCodeLockKey(request.getTemplateCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected QueryTemplateSaveResult processWithinLock(CreateQueryTemplateRequest request) {
        // 保存校验
        support.requirePermission(request.getActor(), request.getSession(),
            DataQueryManagementSupport.TEMPLATE_MANAGE_PERMISSION);
        DataSourceDetail dataSource = dataSources.findDataSource(request.getDraft().getDataSourceId());
        TemplateValidationResult result = validator.validate(request.getDraft(), dataSource,
            support.referencedKeyTypes(request.getDraft()));
        List<TemplateProblem> warnings = DataQueryManagementSupport.withParticipation(
            DataQueryManagementSupport.requireValid(result), result, request.getDraft(), dataSource, true);

        // 本地保存与审计
        QueryTemplateChange change = support.write(request.getActor(), request.getSession(),
            DataQueryManagementSupport.TEMPLATE_MANAGE_PERMISSION, context -> {
                var now = support.now();
                QueryTemplateChange saved = templates.createQueryTemplate(new CreateQueryTemplateCommand(
                    support.nextId(), support.nextId(), request.getTemplateCode(), request.getTemplateName(),
                    request.getDescription(), request.getDraft(), request.getChangeNote(), context.account().id(),
                    now));
                var fact = DataQueryAuditFacts.template(request.getActor(), context.member().tenantId(), saved,
                    note(request.getChangeNote(), "新建查询模板"), now);
                audit.record(() -> fact);
                return saved;
            });
        return new QueryTemplateSaveResult(change, warnings);
    }

    /** 审计说明：优先使用变更说明。 */
    static String note(String changeNote, String fallback) {
        return changeNote == null || changeNote.isBlank() ? fallback : changeNote.strip();
    }
}
