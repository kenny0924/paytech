package com.paytech.platform.support.localization.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.api.localization.response.NamespaceResponseBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nNamespace;
import com.paytech.platform.support.localization.domain.LocalizationCodes;
import com.paytech.platform.support.localization.domainservice.service.LocalizationCatalogService;
import com.paytech.platform.support.localization.service.processor.request.NamespaceCommand;

import lombok.AllArgsConstructor;

/**
 * 在应用下新建命名空间，版本 1
 * <li>持应用资源锁（与 key 导入共用）；应用内编码唯一键为最终保护，已存在返回 400。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateNamespaceProcessor extends AbstractLockProcessor<NamespaceCommand, NamespaceResponseBody> {

    private final LocalizationCatalogService catalog;
    private final LocalizationProcessorSupport support;

    @Override
    protected void validate(NamespaceCommand request) {
        if (!LocalizationCodes.NAMESPACE_CODE.matcher(request.getNamespaceCode()).matches()
            || request.getNamespaceName() == null || request.getNamespaceName().isBlank()) {
            throw new InvalidParameterException("命名空间编码或名称不合法");
        }
    }

    @Override
    protected LockCommand lockCommand(NamespaceCommand request) {
        return LockCommand.tryLock(LocalizationProcessorSupport.applicationLock(request.getApplicationCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected NamespaceResponseBody processWithinLock(NamespaceCommand request) {
        SessionContext session = support.authorize(request.getActor(), request.getSession(),
            LocalizationProcessorSupport.EDIT_PERMISSION);
        I18nApplication application = support.requireApplication(request.getApplicationCode());
        I18nNamespace namespace = newNamespace(application, request, support.actorId(session));
        return support.write(request.getActor(), request.getSession(), LocalizationProcessorSupport.EDIT_PERMISSION,
            () -> {
                catalog.createNamespace(namespace);
                support.recordSucceeded(session, LocalizationProcessorSupport.NAMESPACE_AUDIT_TYPE,
                    namespace.getNamespaceId(), "新建命名空间 " + namespace.getNamespaceCode());
                return LocalizationResponses.namespace(namespace, 0, 0);
            });
    }

    /** 新命名空间。 */
    I18nNamespace newNamespace(I18nApplication application, NamespaceCommand request, long actorId) {
        I18nNamespace namespace = new I18nNamespace();
        namespace.setNamespaceId(support.nextId());
        namespace.setApplicationId(application.getApplicationId());
        namespace.setNamespaceCode(request.getNamespaceCode());
        namespace.setNamespaceName(request.getNamespaceName());
        namespace.setDescription(request.getDescription());
        namespace.setSortOrder(request.getSortOrder());
        namespace.setVersion(1);
        namespace.setCreatedBy(actorId);
        namespace.setUpdatedBy(actorId);
        namespace.setCreateTime(support.now());
        namespace.setUpdateTime(namespace.getCreateTime());
        return namespace;
    }
}
