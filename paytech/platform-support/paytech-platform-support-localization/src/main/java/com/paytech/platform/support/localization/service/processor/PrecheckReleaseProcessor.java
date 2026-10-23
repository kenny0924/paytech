package com.paytech.platform.support.localization.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.localization.response.ReleasePrecheckResponseBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.domain.release.ReleaseAssessment;
import com.paytech.platform.support.localization.domainservice.repository.I18nReleaseRepository;
import com.paytech.platform.support.localization.service.processor.request.ApplicationQuery;

import lombok.AllArgsConstructor;

/**
 * 发布预检：覆盖率、是否可发布、阻止发布的问题、发布报告预览（各最多 200 条）与相对当前发布的变更摘要；复核 I18N_VIEW 后读取，不写库、
 * 不开事务（变更摘要读取线上发布的语言包，在事务外）。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class PrecheckReleaseProcessor extends AbstractProcessor<ApplicationQuery, ReleasePrecheckResponseBody> {

    private final I18nReleaseRepository releases;
    private final ReleaseChangeSummaries summaries;
    private final LocalizationProcessorSupport support;

    @Override
    protected ReleasePrecheckResponseBody processRequest(ApplicationQuery request) {
        support.authorize(request.getActor(), request.getSession(), LocalizationProcessorSupport.VIEW_PERMISSION);
        I18nApplication application = support.requireApplication(request.getApplicationCode());
        ReleaseAssessment assessment = support.assess(application);
        ReleasePrecheckResponseBody body = new ReleasePrecheckResponseBody();
        body.setPublishable(assessment.publishable());
        body.setContentRevision(application.getContentRevision());
        body.setNextReleaseNo(releases.maxReleaseNo(application.getApplicationId()) + 1);
        body.setActiveKeyCount(assessment.getActiveKeyCount());
        body.setCoverage(LocalizationResponses.coverage(assessment.getCoverage()));
        body.setMissing(LocalizationResponses.keys(assessment.getMissing(), LocalizationProblems.LIMIT));
        body.setMissingTotal(assessment.getMissing().size());
        body.setSourceChanged(LocalizationResponses.keys(assessment.getSourceChanged(), LocalizationProblems.LIMIT));
        body.setSourceChangedTotal(assessment.getSourceChanged().size());
        body.setChangeSummary(LocalizationResponses.changeSummary(summaries.summarize(application)));
        body.setProblems(assessment.getProblems().stream().limit(LocalizationProblems.LIMIT)
            .map(LocalizationResponses::problem).toList());
        return body;
    }
}
