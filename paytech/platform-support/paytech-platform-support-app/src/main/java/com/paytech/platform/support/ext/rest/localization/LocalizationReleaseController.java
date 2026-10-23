package com.paytech.platform.support.ext.rest.localization;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.localization.LocalizationReleaseApi;
import com.paytech.platform.support.api.localization.request.ActivateReleaseRequestBody;
import com.paytech.platform.support.api.localization.request.PublishReleaseRequestBody;
import com.paytech.platform.support.api.localization.response.CurrentReleaseResponseBody;
import com.paytech.platform.support.api.localization.response.ReleaseDetailResponseBody;
import com.paytech.platform.support.api.localization.response.ReleaseDiffResponseBody;
import com.paytech.platform.support.api.localization.response.ReleasePrecheckResponseBody;
import com.paytech.platform.support.api.localization.response.ReleaseSummaryResponseBody;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.localization.service.processor.ActivateReleaseProcessor;
import com.paytech.platform.support.localization.service.processor.DiffReleaseProcessor;
import com.paytech.platform.support.localization.service.processor.GetReleaseProcessor;
import com.paytech.platform.support.localization.service.processor.ListReleasesProcessor;
import com.paytech.platform.support.localization.service.processor.PrecheckReleaseProcessor;
import com.paytech.platform.support.localization.service.processor.PublishReleaseProcessor;
import com.paytech.platform.support.localization.service.processor.request.ApplicationQuery;
import com.paytech.platform.support.localization.service.processor.request.PublishReleaseCommand;
import com.paytech.platform.support.localization.service.processor.request.ReleaseListQuery;
import com.paytech.platform.support.localization.service.processor.request.ReleaseQuery;

import lombok.AllArgsConstructor;

/** 发布、历史、详情、差异与切换当前发布的协议适配；发布与切换写审计（技术方案 3.3）。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class LocalizationReleaseController implements LocalizationReleaseApi {

    private final LocalizationConverter converter;
    private final PublishReleaseProcessor publishProcessor;
    private final ActivateReleaseProcessor activateProcessor;
    private final DiffReleaseProcessor diffProcessor;
    private final GetReleaseProcessor getProcessor;
    private final ListReleasesProcessor listProcessor;
    private final PrecheckReleaseProcessor precheckProcessor;

    /** 发布预检。 */
    @Override
    public ReleasePrecheckResponseBody precheckRelease(String actor, String session, String applicationCode) {
        return precheckProcessor.process(new ApplicationQuery(actor, session, applicationCode));
    }

    /** 发布。 */
    @Audit(action = "I18N_RELEASE_PUBLISH", resourceType = "I18N_RELEASE", fallbackResourceId = "I18N_RELEASE_CATALOG")
    @Override
    public ReleaseSummaryResponseBody publishRelease(String actor, String session, String applicationCode,
        PublishReleaseRequestBody request) {
        return publishProcessor.process(new PublishReleaseCommand(actor, session, applicationCode,
            LocalizationConverter.text(request.getReleaseNote())));
    }

    /** 发布历史。 */
    @Override
    public PagingResponseBody<ReleaseSummaryResponseBody> listReleases(String actor, String session,
        String applicationCode, Integer page, Integer size) {
        return listProcessor.process(new ReleaseListQuery(actor, session, applicationCode,
            PagingRequestBody.of(page, size)));
    }

    /** 发布详情。 */
    @Override
    public ReleaseDetailResponseBody getRelease(String actor, String session, String applicationCode,
        Integer releaseNo) {
        return getProcessor.process(new ReleaseQuery(actor, session, applicationCode, positive(releaseNo, "releaseNo"),
            null));
    }

    /** 两次发布的差异。 */
    @Override
    public ReleaseDiffResponseBody diffRelease(String actor, String session, String applicationCode,
        Integer releaseNo, Integer baseReleaseNo) {
        return diffProcessor.process(new ReleaseQuery(actor, session, applicationCode, positive(releaseNo, "releaseNo"),
            baseReleaseNo == null ? null : positive(baseReleaseNo, "baseReleaseNo")));
    }

    /** 切换当前发布。 */
    @Audit(action = "I18N_RELEASE_ACTIVATE", resourceType = "I18N_RELEASE", fallbackResourceId = "I18N_RELEASE_CATALOG")
    @Override
    public CurrentReleaseResponseBody activateRelease(String actor, String session, String applicationCode,
        ActivateReleaseRequestBody request) {
        return activateProcessor.process(converter.activateRelease(actor, session, applicationCode, request));
    }

    /** 发布号须为正整数。 */
    static int positive(Integer value, String field) {
        if (value == null || value < 1) {
            throw new InvalidParameterException(field + " 必须为正整数");
        }
        return value;
    }
}
