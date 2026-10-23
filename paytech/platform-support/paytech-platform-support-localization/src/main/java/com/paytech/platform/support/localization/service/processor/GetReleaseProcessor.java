package com.paytech.platform.support.localization.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.api.localization.response.ReleaseDetailResponseBody;
import com.paytech.platform.support.api.localization.response.ReleaseReport;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nRelease;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nReleaseBundle;
import com.paytech.platform.support.localization.domain.LocalizationJson;
import com.paytech.platform.support.localization.domain.release.ReleaseReportFact;
import com.paytech.platform.support.localization.domainservice.repository.I18nReleaseBundleRepository;
import com.paytech.platform.support.localization.integration.ReleaseBundleFileChecker;
import com.paytech.platform.support.localization.service.processor.request.ReleaseQuery;

import lombok.AllArgsConstructor;

/**
 * 发布详情：摘要、覆盖率、发布报告、语言包与文件检查进度；复核 I18N_VIEW 后读取，不开事务（检查进度经 file-document 进程内查询）。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetReleaseProcessor extends AbstractProcessor<ReleaseQuery, ReleaseDetailResponseBody> {

    private final LocalizationQueryLookups lookups;
    private final I18nReleaseBundleRepository bundles;
    private final ReleaseBundleFileChecker checker;
    private final LocalizationProcessorSupport support;

    @Override
    protected ReleaseDetailResponseBody processRequest(ReleaseQuery request) {
        support.authorize(request.getActor(), request.getSession(), LocalizationProcessorSupport.VIEW_PERMISSION);
        I18nApplication application = support.requireApplication(request.getApplicationCode());
        I18nRelease release = lookups.requireRelease(application, request.getReleaseNo());
        ReleaseReportFact report = LocalizationJson.readReport(release.getReportJson());
        ReleaseDetailResponseBody body = new ReleaseDetailResponseBody();
        body.setRelease(LocalizationResponses.release(release, application.getCurrentReleaseId()));
        body.setBasedOnContentRevision(release.getBasedOnContentRevision());
        body.setCoverage(LocalizationResponses.coverage(LocalizationJson.readCoverage(release.getCoverageJson())));
        ReleaseReport reportBody = new ReleaseReport();
        reportBody.setMissing(LocalizationResponses.keys(report.getMissing(), Integer.MAX_VALUE));
        reportBody.setSourceChanged(LocalizationResponses.keys(report.getSourceChanged(), Integer.MAX_VALUE));
        body.setReport(reportBody);
        List<I18nReleaseBundle> rows = bundles.findByRelease(release.getReleaseId());
        body.setCheckProgress(LocalizationResponses.checkProgress(rows.stream()
            .map(row -> checker.state(FileAddress.parse(row.getFileAddress()))).toList()));
        body.setBundles(rows.stream().map(LocalizationResponses::bundle).toList());
        return body;
    }
}
