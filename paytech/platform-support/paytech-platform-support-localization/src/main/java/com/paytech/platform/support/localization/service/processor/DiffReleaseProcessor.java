package com.paytech.platform.support.localization.service.processor;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.localization.response.ReleaseDiffResponseBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nRelease;
import com.paytech.platform.support.localization.domain.release.ReleaseDiff;
import com.paytech.platform.support.localization.domainservice.repository.I18nReleaseRepository;
import com.paytech.platform.support.localization.integration.ReleaseBundleReader;
import com.paytech.platform.support.localization.service.processor.request.ReleaseQuery;

/**
 * 两次发布的差异（需求 R6、AC-10）：复核 I18N_VIEW 后读取两次发布的语言包内容对比；基准缺省为小于该发布号的最大发布号，没有时全部为新增。
 * <li>语言包内容经 {@link ReleaseBundleReader} 在事务外读取；端口未接入时返回 503。</li>
 */
@Component
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class DiffReleaseProcessor extends AbstractProcessor<ReleaseQuery, ReleaseDiffResponseBody> {

    private final LocalizationQueryLookups lookups;
    private final I18nReleaseRepository releases;
    private final ReleaseChangeSummaries contents;
    private final LocalizationProcessorSupport support;
    private final ObjectProvider<ReleaseBundleReader> reader;

    /**
     * 装配依赖；语言包读取端口的实现由集成卡提供。
     *
     * @param lookups 查询查找
     * @param releases 发布仓储
     * @param contents 发布内容读取
     * @param support 公共能力
     * @param reader 语言包读取端口
     */
    public DiffReleaseProcessor(LocalizationQueryLookups lookups, I18nReleaseRepository releases,
        ReleaseChangeSummaries contents, LocalizationProcessorSupport support,
        ObjectProvider<ReleaseBundleReader> reader) {
        this.lookups = lookups;
        this.releases = releases;
        this.contents = contents;
        this.support = support;
        this.reader = reader;
    }

    @Override
    protected ReleaseDiffResponseBody processRequest(ReleaseQuery request) {
        support.authorize(request.getActor(), request.getSession(), LocalizationProcessorSupport.VIEW_PERMISSION);
        I18nApplication application = support.requireApplication(request.getApplicationCode());
        I18nRelease target = lookups.requireRelease(application, request.getReleaseNo());
        Integer baseNo = request.getBaseReleaseNo() != null ? request.getBaseReleaseNo()
            : releases.previousReleaseNo(application.getApplicationId(), request.getReleaseNo());
        I18nRelease base = baseNo == null ? null : lookups.requireRelease(application, baseNo);
        ReleaseBundleReader bundleReader = reader.getIfAvailable();
        if (bundleReader == null) {
            throw new ServiceUnavailableException();
        }
        ReleaseDiff diff = ReleaseDiff.between(base == null ? Map.of() : contents.read(bundleReader, base),
            contents.read(bundleReader, target));
        ReleaseDiffResponseBody body = new ReleaseDiffResponseBody();
        body.setReleaseNo(request.getReleaseNo());
        body.setBaseReleaseNo(baseNo);
        body.setAdded(diff.getAdded().stream().map(LocalizationResponses::change).toList());
        body.setChanged(diff.getChanged().stream().map(LocalizationResponses::change).toList());
        body.setRemoved(diff.getRemoved().stream().map(LocalizationResponses::change).toList());
        return body;
    }
}
