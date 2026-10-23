package com.paytech.platform.support.localization.service.processor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.api.localization.response.ManifestBundle;
import com.paytech.platform.support.api.localization.response.ManifestLocale;
import com.paytech.platform.support.api.localization.response.ManifestResponseBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nLocale;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nRelease;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nReleaseBundle;
import com.paytech.platform.support.localization.core.enums.ApplicationStatus;
import com.paytech.platform.support.localization.core.enums.LocaleStatus;
import com.paytech.platform.support.localization.core.enums.ReleaseStatus;
import com.paytech.platform.support.localization.domain.LocalizationCodes;
import com.paytech.platform.support.localization.domain.release.ReleaseBundleLinks;
import com.paytech.platform.support.localization.domain.release.ReleaseBundleLinks.BundleLink;
import com.paytech.platform.support.localization.domainservice.repository.I18nApplicationRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nLocaleRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nReleaseBundleRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nReleaseRepository;
import com.paytech.platform.support.localization.integration.ReleaseBundleStore;
import com.paytech.platform.support.localization.service.processor.request.ManifestQuery;

/**
 * 读取应用当前或指定发布的 manifest（技术方案 3.7）；匿名，不开事务。
 * <li>应用不存在或已停用、指定发布不存在或不是 READY 返回 404；还没有 READY 发布时 releaseNo 为 null、bundles 为空。</li>
 * <li>每次请求按发布读取语言包行并经 {@link ReleaseBundleStore#publicUrl} 计算公开地址，不做进程内缓存（ETag / 304 由 BFF 负责）；
 * 端口未接入且已有发布时返回 503。停用语言立即从清单消失；bundles 只含 en 与目标语言。</li>
 */
@Component
public class GetManifestProcessor extends AbstractProcessor<ManifestQuery, ManifestResponseBody> {

    private final I18nLocaleRepository locales;
    private final I18nReleaseRepository releases;
    private final I18nReleaseBundleRepository bundles;
    private final I18nApplicationRepository applications;
    private final ObjectProvider<ReleaseBundleStore> store;

    /**
     * 装配依赖；语言包存储端口的实现由集成卡提供。
     *
     * @param locales 语言仓储
     * @param releases 发布仓储
     * @param bundles 语言包仓储
     * @param applications 应用仓储
     * @param store 语言包存储端口
     */
    public GetManifestProcessor(I18nLocaleRepository locales, I18nReleaseRepository releases,
        I18nReleaseBundleRepository bundles, I18nApplicationRepository applications,
        ObjectProvider<ReleaseBundleStore> store) {
        this.locales = locales;
        this.releases = releases;
        this.bundles = bundles;
        this.applications = applications;
        this.store = store;
    }

    @Override
    protected ManifestResponseBody processRequest(ManifestQuery request) {
        I18nApplication application = applications.findByCode(request.getApplicationCode());
        if (application == null || application.getStatus() != ApplicationStatus.ENABLED) {
            throw LocalizationProcessorSupport.notFound();
        }
        I18nRelease release = resolveRelease(application, request.getReleaseNo());
        ManifestResponseBody body = new ManifestResponseBody();
        body.setApplication(application.getApplicationCode());
        body.setDefaultLocale(application.getDefaultLocale());
        if (release == null) {
            body.setLocales(List.of());
            body.setBundles(Map.of());
            return body;
        }
        ReleaseBundleLinks links = links(release.getReleaseId());
        List<I18nLocale> available = locales.findAll().stream()
            .filter(locale -> locale.getStatus() == LocaleStatus.ENABLED)
            .filter(locale -> links.bundles().containsKey(locale.getLocaleCode())).toList();
        body.setReleaseNo(release.getReleaseNo());
        body.setPublishedAt(LocalizationResponses.time(release.getReadyTime()));
        body.setLocales(available.stream().map(GetManifestProcessor::locale).toList());
        body.setBundles(bundles(links, available, request.getLocale()));
        return body;
    }

    /** 指定发布须为 READY；未指定时取当前发布，没有为 null。 */
    I18nRelease resolveRelease(I18nApplication application, Integer releaseNo) {
        if (releaseNo == null) {
            return application.getCurrentReleaseId() == null ? null
                : releases.findById(application.getCurrentReleaseId());
        }
        I18nRelease release = releases.findByNo(application.getApplicationId(), releaseNo);
        if (release == null || release.getStatus() != ReleaseStatus.READY) {
            throw LocalizationProcessorSupport.notFound();
        }
        return release;
    }

    /** 发布全部语言包的公开地址；端口未接入时 503。 */
    ReleaseBundleLinks links(long releaseId) {
        ReleaseBundleStore available = store.getIfAvailable();
        if (available == null) {
            throw new ServiceUnavailableException();
        }
        Map<String, Map<String, BundleLink>> links = new TreeMap<>();
        for (I18nReleaseBundle bundle : bundles.findByRelease(releaseId)) {
            links.computeIfAbsent(bundle.getLocaleCode(), ignored -> new TreeMap<>()).put(bundle.getNamespaceCode(),
                new BundleLink(bundle.getContentHash(), available.publicUrl(FileAddress.parse(bundle.getFileAddress()))));
        }
        links.replaceAll((locale, namespaces) -> Map.copyOf(namespaces));
        return new ReleaseBundleLinks(links);
    }

    /**
     * 只保留 en 与目标语言，且语言须可切换。
     * <li>目标语言为空或就是基准语言 en 时只取 en；不能把相同元素交给 {@code Set.of}，否则会抛重复元素异常。</li>
     */
    static Map<String, Map<String, ManifestBundle>> bundles(ReleaseBundleLinks links, List<I18nLocale> available,
        String targetLocale) {
        Set<String> wanted = targetLocale == null || LocalizationCodes.BASE_LOCALE.equals(targetLocale)
            ? Set.of(LocalizationCodes.BASE_LOCALE) : Set.of(LocalizationCodes.BASE_LOCALE, targetLocale);
        Map<String, Map<String, ManifestBundle>> result = new LinkedHashMap<>();
        for (I18nLocale locale : available) {
            if (!wanted.contains(locale.getLocaleCode())) {
                continue;
            }
            Map<String, ManifestBundle> namespaces = new LinkedHashMap<>();
            links.bundles().get(locale.getLocaleCode()).entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> namespaces.put(entry.getKey(), bundle(entry.getValue())));
            result.put(locale.getLocaleCode(), namespaces);
        }
        return result;
    }

    /** 可切换语言。 */
    static ManifestLocale locale(I18nLocale row) {
        ManifestLocale body = new ManifestLocale();
        body.setCode(row.getLocaleCode());
        body.setNativeName(row.getNativeName());
        body.setDirection(row.getTextDirection().getCode());
        return body;
    }

    /** 语言包地址与摘要。 */
    static ManifestBundle bundle(BundleLink link) {
        ManifestBundle body = new ManifestBundle();
        body.setHash(link.hash());
        body.setUrl(link.url());
        return body;
    }
}
