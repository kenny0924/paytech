package com.paytech.platform.support.localization.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.localization.response.ApplicationListResponseBody;
import com.paytech.platform.support.localization.domainservice.repository.I18nApplicationRepository;
import com.paytech.platform.support.localization.service.processor.request.OperatorQuery;

import lombok.AllArgsConstructor;

/**
 * 全部应用、当前发布号与未发布变更数：复核 I18N_VIEW 后读取，按编码升序；不开事务。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListApplicationsProcessor extends AbstractProcessor<OperatorQuery, ApplicationListResponseBody> {

    private final LocalizationQueryLookups lookups;
    private final LocalizationProcessorSupport support;
    private final I18nApplicationRepository applications;

    @Override
    protected ApplicationListResponseBody processRequest(OperatorQuery request) {
        support.authorize(request.getActor(), request.getSession(), LocalizationProcessorSupport.VIEW_PERMISSION);
        ApplicationListResponseBody body = new ApplicationListResponseBody();
        body.setItems(applications.findAll().stream()
            .map(application -> LocalizationResponses.application(application, lookups.currentReleaseNo(application),
                lookups.pendingChangeCount(application)))
            .toList());
        return body;
    }
}
