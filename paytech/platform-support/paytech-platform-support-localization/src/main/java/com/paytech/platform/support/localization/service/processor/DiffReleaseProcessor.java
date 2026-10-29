package com.paytech.platform.support.localization.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.localization.response.ReleaseDiffResponseBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.domain.release.ReleaseDiff;
import com.paytech.platform.support.localization.domainservice.repository.I18nReleaseRepository;
import com.paytech.platform.support.localization.service.processor.request.ReleaseQuery;

import lombok.AllArgsConstructor;

/**
 * 两次发布的差异（需求 R6、AC-10）：复核 I18N_VIEW 后由发布变更明细还原基准与目标发布的内容并比较；基准缺省为小于该发布号的最大发布号，
 * 没有时全部为新增。只查本库，不读语言包文件，不开事务。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class DiffReleaseProcessor extends AbstractProcessor<ReleaseQuery, ReleaseDiffResponseBody> {

    private final LocalizationQueryLookups lookups;
    private final I18nReleaseRepository releases;
    private final ReleaseChangeSummaries contents;
    private final LocalizationProcessorSupport support;

    @Override
    protected ReleaseDiffResponseBody processRequest(ReleaseQuery request) {
        support.authorize(request.getActor(), request.getSession(), LocalizationProcessorSupport.VIEW_PERMISSION);
        I18nApplication application = support.requireApplication(request.getApplicationCode());
        lookups.requireRelease(application, request.getReleaseNo());
        Integer baseNo = request.getBaseReleaseNo() != null ? request.getBaseReleaseNo()
            : releases.previousReleaseNo(application.getApplicationId(), request.getReleaseNo());
        if (baseNo != null) {
            lookups.requireRelease(application, baseNo);
        }
        ReleaseDiff diff = ReleaseDiff.between(contents.restore(application, baseNo == null ? 0 : baseNo),
            contents.restore(application, request.getReleaseNo()));
        ReleaseDiffResponseBody body = new ReleaseDiffResponseBody();
        body.setReleaseNo(request.getReleaseNo());
        body.setBaseReleaseNo(baseNo);
        body.setAdded(diff.getAdded().stream().map(LocalizationResponses::change).toList());
        body.setChanged(diff.getChanged().stream().map(LocalizationResponses::change).toList());
        body.setRemoved(diff.getRemoved().stream().map(LocalizationResponses::change).toList());
        return body;
    }
}
