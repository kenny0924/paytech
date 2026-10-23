package com.paytech.platform.support.localization.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.localization.response.ApplicationDetailResponseBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nRelease;
import com.paytech.platform.support.localization.core.enums.KeyStatus;
import com.paytech.platform.support.localization.domain.NamespaceKeyCount;
import com.paytech.platform.support.localization.domainservice.repository.I18nKeyRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nNamespaceRepository;
import com.paytech.platform.support.localization.service.processor.request.ApplicationQuery;

import lombok.AllArgsConstructor;

/**
 * 应用详情：设置、当前发布、未发布变更数、key 数与覆盖率摘要；复核 I18N_VIEW 后读取，不开事务。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetApplicationProcessor extends AbstractProcessor<ApplicationQuery, ApplicationDetailResponseBody> {

    private final I18nKeyRepository keys;
    private final LocalizationQueryLookups lookups;
    private final I18nNamespaceRepository namespaces;
    private final LocalizationProcessorSupport support;

    @Override
    protected ApplicationDetailResponseBody processRequest(ApplicationQuery request) {
        support.authorize(request.getActor(), request.getSession(), LocalizationProcessorSupport.VIEW_PERMISSION);
        I18nApplication application = support.requireApplication(request.getApplicationCode());
        I18nRelease current = lookups.currentRelease(application);
        List<NamespaceKeyCount> counts = keys.countByNamespace(application.getApplicationId());
        ApplicationDetailResponseBody body = new ApplicationDetailResponseBody();
        body.setApplication(LocalizationResponses.application(application,
            current == null ? null : current.getReleaseNo(), lookups.pendingChangeCount(application)));
        body.setCurrentRelease(current == null ? null : LocalizationResponses.release(current, current.getReleaseId()));
        body.setActiveKeyCount(LocalizationQueryLookups.sum(counts, KeyStatus.ACTIVE, null));
        body.setDeprecatedKeyCount(LocalizationQueryLookups.sum(counts, KeyStatus.DEPRECATED, null));
        body.setNamespaceCount(namespaces.findByApplication(application.getApplicationId()).size());
        body.setCoverage(LocalizationResponses.coverage(support.assess(application).getCoverage()));
        return body;
    }
}
