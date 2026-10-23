package com.paytech.platform.support.localization.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nNamespace;
import com.paytech.platform.support.localization.domainservice.repository.I18nNamespaceRepository;
import com.paytech.platform.support.localization.domainservice.service.LocalizationCatalogService;
import com.paytech.platform.support.localization.service.processor.request.NamespaceCommand;

import lombok.AllArgsConstructor;

/**
 * 删除命名空间（物理删除）
 * <li>持应用资源锁；写事务内锁应用行（与 key 导入串行）后重证其下没有任何 key（含已废弃），再按原版本删除。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class DeleteNamespaceProcessor extends AbstractLockProcessor<NamespaceCommand, Void> {

    private final I18nNamespaceRepository namespaces;
    private final LocalizationCatalogService catalog;
    private final LocalizationProcessorSupport support;

    @Override
    protected void validate(NamespaceCommand request) {
        if (request.getExpectedVersion() == null) {
            throw new InvalidParameterException("删除命名空间须提供 expectedVersion");
        }
    }

    @Override
    protected LockCommand lockCommand(NamespaceCommand request) {
        return LockCommand.tryLock(LocalizationProcessorSupport.applicationLock(request.getApplicationCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected Void processWithinLock(NamespaceCommand request) {
        SessionContext session = support.authorize(request.getActor(), request.getSession(),
            LocalizationProcessorSupport.EDIT_PERMISSION);
        I18nApplication application = support.requireApplication(request.getApplicationCode());
        I18nNamespace namespace = namespaces.findByCode(application.getApplicationId(), request.getNamespaceCode());
        if (namespace == null) {
            throw LocalizationProcessorSupport.notFound();
        }
        LocalizationProcessorSupport.requireVersion(namespace.getVersion(), request.getExpectedVersion(),
            "命名空间 " + namespace.getNamespaceCode());
        return support.write(request.getActor(), request.getSession(), LocalizationProcessorSupport.EDIT_PERMISSION,
            () -> {
                catalog.deleteNamespace(application, namespace);
                support.recordSucceeded(session, LocalizationProcessorSupport.NAMESPACE_AUDIT_TYPE,
                    namespace.getNamespaceId(), "删除命名空间 " + namespace.getNamespaceCode());
                return null;
            });
    }
}
