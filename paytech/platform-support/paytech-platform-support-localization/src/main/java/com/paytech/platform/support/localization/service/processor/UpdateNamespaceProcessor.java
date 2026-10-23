package com.paytech.platform.support.localization.service.processor;

import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.api.localization.response.NamespaceResponseBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nNamespace;
import com.paytech.platform.support.localization.core.enums.KeyStatus;
import com.paytech.platform.support.localization.domain.NamespaceKeyCount;
import com.paytech.platform.support.localization.domainservice.repository.I18nKeyRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nNamespaceRepository;
import com.paytech.platform.support.localization.domainservice.service.LocalizationCatalogService;
import com.paytech.platform.support.localization.service.processor.request.NamespaceCommand;

import lombok.AllArgsConstructor;

/**
 * 修改命名空间名称、说明与排序，版本加一；编码不可修改
 * <li>持应用资源锁；与当前完全相同时不写入。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateNamespaceProcessor extends AbstractLockProcessor<NamespaceCommand, NamespaceResponseBody> {

    private final I18nKeyRepository keys;
    private final I18nNamespaceRepository namespaces;
    private final LocalizationCatalogService catalog;
    private final LocalizationProcessorSupport support;

    @Override
    protected void validate(NamespaceCommand request) {
        if (request.getExpectedVersion() == null || request.getNamespaceName() == null
            || request.getNamespaceName().isBlank()) {
            throw new InvalidParameterException("修改命名空间须提供 expectedVersion 与名称");
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
        I18nNamespace namespace = requireNamespace(application, request.getNamespaceCode());
        LocalizationProcessorSupport.requireVersion(namespace.getVersion(), request.getExpectedVersion(),
            "命名空间 " + namespace.getNamespaceCode());
        I18nNamespace next = next(request, support.actorId(session));
        if (!same(namespace, next)) {
            support.write(request.getActor(), request.getSession(), LocalizationProcessorSupport.EDIT_PERMISSION, () -> {
                catalog.updateNamespace(namespace, next);
                support.recordSucceeded(session, LocalizationProcessorSupport.NAMESPACE_AUDIT_TYPE,
                    namespace.getNamespaceId(), "修改命名空间 " + namespace.getNamespaceCode());
                return null;
            });
        }
        return response(application, namespace);
    }

    /** 命名空间不存在返回 404。 */
    I18nNamespace requireNamespace(I18nApplication application, String namespaceCode) {
        I18nNamespace namespace = namespaces.findByCode(application.getApplicationId(), namespaceCode);
        if (namespace == null) {
            throw LocalizationProcessorSupport.notFound();
        }
        return namespace;
    }

    /** 目标值。 */
    I18nNamespace next(NamespaceCommand request, long actorId) {
        I18nNamespace next = new I18nNamespace();
        next.setNamespaceName(request.getNamespaceName());
        next.setDescription(request.getDescription());
        next.setSortOrder(request.getSortOrder());
        next.setUpdatedBy(actorId);
        next.setUpdateTime(support.now());
        return next;
    }

    /** 可修改字段是否全部相同。 */
    static boolean same(I18nNamespace namespace, I18nNamespace next) {
        return Objects.equals(namespace.getNamespaceName(), next.getNamespaceName())
            && Objects.equals(namespace.getDescription(), next.getDescription())
            && Objects.equals(namespace.getSortOrder(), next.getSortOrder());
    }

    /** 带 key 数的响应。 */
    NamespaceResponseBody response(I18nApplication application, I18nNamespace namespace) {
        long active = 0;
        long deprecated = 0;
        for (NamespaceKeyCount count : keys.countByNamespace(application.getApplicationId())) {
            if (!namespace.getNamespaceId().equals(count.getNamespaceId())) {
                continue;
            }
            if (count.getStatus() == KeyStatus.ACTIVE) {
                active = count.getKeyCount();
            } else {
                deprecated = count.getKeyCount();
            }
        }
        return LocalizationResponses.namespace(namespace, active, deprecated);
    }
}
