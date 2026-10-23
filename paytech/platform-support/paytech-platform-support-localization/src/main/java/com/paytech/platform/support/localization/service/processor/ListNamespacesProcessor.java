package com.paytech.platform.support.localization.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.localization.response.NamespaceListResponseBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.enums.KeyStatus;
import com.paytech.platform.support.localization.domain.NamespaceKeyCount;
import com.paytech.platform.support.localization.domainservice.repository.I18nKeyRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nNamespaceRepository;
import com.paytech.platform.support.localization.service.processor.request.ApplicationQuery;

import lombok.AllArgsConstructor;

/**
 * 应用的全部命名空间与在用 / 已废弃 key 数：复核 I18N_VIEW 后读取，不开事务。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListNamespacesProcessor extends AbstractProcessor<ApplicationQuery, NamespaceListResponseBody> {

    private final I18nKeyRepository keys;
    private final I18nNamespaceRepository namespaces;
    private final LocalizationProcessorSupport support;

    @Override
    protected NamespaceListResponseBody processRequest(ApplicationQuery request) {
        support.authorize(request.getActor(), request.getSession(), LocalizationProcessorSupport.VIEW_PERMISSION);
        I18nApplication application = support.requireApplication(request.getApplicationCode());
        List<NamespaceKeyCount> counts = keys.countByNamespace(application.getApplicationId());
        NamespaceListResponseBody body = new NamespaceListResponseBody();
        body.setItems(namespaces.findByApplication(application.getApplicationId()).stream()
            .map(namespace -> LocalizationResponses.namespace(namespace,
                LocalizationQueryLookups.sum(counts, KeyStatus.ACTIVE, namespace),
                LocalizationQueryLookups.sum(counts, KeyStatus.DEPRECATED, namespace)))
            .toList());
        return body;
    }
}
