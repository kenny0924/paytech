package com.paytech.platform.support.localization.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.localization.response.ReleaseSummaryResponseBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.domainservice.repository.I18nReleaseRepository;
import com.paytech.platform.support.localization.service.processor.request.ReleaseListQuery;

import lombok.AllArgsConstructor;

/**
 * 发布历史：复核 I18N_VIEW 后按发布号倒序分页读取，不开事务。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListReleasesProcessor extends AbstractProcessor<ReleaseListQuery, PagingResponseBody<ReleaseSummaryResponseBody>> {

    private final I18nReleaseRepository releases;
    private final LocalizationProcessorSupport support;

    @Override
    protected PagingResponseBody<ReleaseSummaryResponseBody> processRequest(ReleaseListQuery request) {
        support.authorize(request.getActor(), request.getSession(), LocalizationProcessorSupport.VIEW_PERMISSION);
        I18nApplication application = support.requireApplication(request.getApplicationCode());
        List<ReleaseSummaryResponseBody> items = releases.findPage(application.getApplicationId(), request.getPaging())
            .stream().map(release -> LocalizationResponses.release(release, application.getCurrentReleaseId())).toList();
        return new PagingResponseBody<>(items, request.getPaging().page(), request.getPaging().size(),
            releases.count(application.getApplicationId()));
    }
}
