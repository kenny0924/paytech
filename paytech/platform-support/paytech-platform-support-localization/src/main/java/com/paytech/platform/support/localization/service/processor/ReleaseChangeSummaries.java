package com.paytech.platform.support.localization.service.processor;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nRelease;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nReleaseBundle;
import com.paytech.platform.support.localization.core.enums.KeyStatus;
import com.paytech.platform.support.localization.domain.LocalizationCodes;
import com.paytech.platform.support.localization.domain.release.BundleContent;
import com.paytech.platform.support.localization.domain.release.ReleaseChangeSummaryFact;
import com.paytech.platform.support.localization.domain.release.ReleaseContent;
import com.paytech.platform.support.localization.domain.release.ReleaseDiff;
import com.paytech.platform.support.localization.domainservice.repository.I18nKeyRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nReleaseBundleRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nTranslationRepository;
import com.paytech.platform.support.localization.integration.ReleaseBundleReader;

/**
 * 预检与发布共用的“相对线上发布”变更摘要，以及按发布读取语言包内容；在事务外调用（读取语言包是对文件服务的调用）。
 * <li>线上发布的内容以它的语言包文件为准（经 {@link ReleaseBundleReader}），当前内容按发布口径由库内文案展开。</li>
 * <li>没有当前发布时与空内容比较；已有当前发布而读取端口未接入时返回 null，不伪造摘要。</li>
 */
@Component
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ReleaseChangeSummaries {

    private final I18nKeyRepository keys;
    private final LocalizationQueryLookups lookups;
    private final I18nReleaseBundleRepository bundles;
    private final I18nTranslationRepository translations;
    private final LocalizationProcessorSupport support;
    private final ObjectProvider<ReleaseBundleReader> reader;

    /**
     * 装配依赖；语言包读取端口的实现由集成卡提供。
     *
     * @param keys key 仓储
     * @param lookups 查询查找
     * @param bundles 语言包仓储
     * @param translations 翻译仓储
     * @param support 公共能力
     * @param reader 语言包读取端口
     */
    public ReleaseChangeSummaries(I18nKeyRepository keys, LocalizationQueryLookups lookups,
        I18nReleaseBundleRepository bundles, I18nTranslationRepository translations,
        LocalizationProcessorSupport support, ObjectProvider<ReleaseBundleReader> reader) {
        this.keys = keys;
        this.lookups = lookups;
        this.bundles = bundles;
        this.translations = translations;
        this.support = support;
        this.reader = reader;
    }

    /**
     * 当前文案相对应用当前发布的变更摘要。
     *
     * @param application 应用
     * @return 摘要；已有当前发布而读取端口未接入时为 null
     */
    ReleaseChangeSummaryFact summarize(I18nApplication application) {
        I18nRelease current = lookups.currentRelease(application);
        Map<String, Map<String, String>> base = Map.of();
        if (current != null) {
            ReleaseBundleReader available = reader.getIfAvailable();
            if (available == null) {
                return null;
            }
            base = read(available, current);
        }
        Map<String, Map<String, String>> target = ReleaseContent.current(
            keys.findByStatus(application.getApplicationId(), KeyStatus.ACTIVE),
            translations.findActiveByApplication(application.getApplicationId()),
            support.enabledLocales().stream().filter(locale -> !LocalizationCodes.BASE_LOCALE.equals(locale)).toList());
        return ReleaseChangeSummaryFact.of(ReleaseDiff.between(base, target),
            current == null ? null : current.getReleaseNo());
    }

    /**
     * 发布的全部文案：语言 → key → 文案，逐个读取语言包。
     *
     * @param bundleReader 语言包读取端口
     * @param release 发布
     * @return 内容
     */
    Map<String, Map<String, String>> read(ReleaseBundleReader bundleReader, I18nRelease release) {
        Map<String, Map<String, String>> texts = new HashMap<>();
        for (I18nReleaseBundle bundle : bundles.findByRelease(release.getReleaseId())) {
            texts.computeIfAbsent(bundle.getLocaleCode(), ignored -> new TreeMap<>())
                .putAll(BundleContent.read(bundleReader.read(FileAddress.parse(bundle.getFileAddress()))));
        }
        return texts;
    }
}
