package com.paytech.platform.support.dataquery.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.dataquery.domain.ChangeQueryTemplateStatusCommand;
import com.paytech.platform.support.dataquery.domain.QueryTemplateChange;
import com.paytech.platform.support.dataquery.domainservice.service.QueryTemplateService;
import com.paytech.platform.support.dataquery.service.processor.request.ChangeQueryTemplateStatusRequest;

import lombok.AllArgsConstructor;

/**
 * 启用或停用模板，不生成新版本
 * <li>按模板持资源锁，在一个本地事务内复核权限、核对原版本（启用时对当前版本数据源加共享锁确认 ACTIVE）、条件更新并登记审计。</li>
 * <li>目标状态与当前相同时不递增版本、不登记审计。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ChangeQueryTemplateStatusProcessor
    extends AbstractLockProcessor<ChangeQueryTemplateStatusRequest, QueryTemplateChange> {

    private final OperationsAuditService audit;
    private final QueryTemplateService templates;
    private final DataQueryManagementSupport support;

    @Override
    protected LockCommand lockCommand(ChangeQueryTemplateStatusRequest request) {
        return LockCommand.tryLock(DataQueryManagementSupport.templateLockKey(request.getTemplateId()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected QueryTemplateChange processWithinLock(ChangeQueryTemplateStatusRequest request) {
        return support.write(request.getActor(), request.getSession(),
            DataQueryManagementSupport.TEMPLATE_MANAGE_PERMISSION, context -> {
                var now = support.now();
                QueryTemplateChange saved = templates.changeQueryTemplateStatus(new ChangeQueryTemplateStatusCommand(
                    request.getTemplateId(), request.getExpectedVersion(), request.getTargetStatus(),
                    context.account().id(), now));
                if (!saved.changed()) {
                    audit.suppressReplay();
                    return saved;
                }
                var fact = DataQueryAuditFacts.template(request.getActor(), context.member().tenantId(), saved,
                    request.getTargetStatus() == QueryTemplateStatus.ACTIVE ? "启用查询模板" : "停用查询模板", now);
                audit.record(() -> fact);
                return saved;
            });
    }
}
