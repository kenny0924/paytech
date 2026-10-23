package com.paytech.platform.support.localization.service.processor;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import com.paytech.platform.support.api.localization.response.ApplicationResponseBody;
import com.paytech.platform.support.api.localization.response.BundleCheckProgress;
import com.paytech.platform.support.api.localization.response.LocaleCoverage;
import com.paytech.platform.support.api.localization.response.LocaleKey;
import com.paytech.platform.support.api.localization.response.LocaleResponseBody;
import com.paytech.platform.support.api.localization.response.LocaleTranslationChange;
import com.paytech.platform.support.api.localization.response.NamespaceResponseBody;
import com.paytech.platform.support.api.localization.response.ReleaseBundleSummary;
import com.paytech.platform.support.api.localization.response.ReleaseChangeSummary;
import com.paytech.platform.support.api.localization.response.ReleaseSummaryResponseBody;
import com.paytech.platform.support.api.localization.response.ReleaseTextChange;
import com.paytech.platform.support.api.localization.response.TranslationEntryResponseBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nLocale;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nNamespace;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nRelease;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nReleaseBundle;
import com.paytech.platform.support.localization.core.enums.BundleFileState;
import com.paytech.platform.support.localization.domain.LocalizationJson;
import com.paytech.platform.support.localization.domain.LocalizationProblem;
import com.paytech.platform.support.localization.domain.TranslationEntryView;
import com.paytech.platform.support.localization.domain.icu.IcuMessageValidator;
import com.paytech.platform.support.localization.domain.release.LocaleCoverageFact;
import com.paytech.platform.support.localization.domain.release.LocaleKeyRef;
import com.paytech.platform.support.localization.domain.release.ReleaseChangeSummaryFact;
import com.paytech.platform.support.localization.domain.release.TextChange;

/** 本模块数据对象到 API 响应体的转换；DataObject 不越过模块边界。 */
final class LocalizationResponses {

    private static final DateTimeFormatter MICROS = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

    private LocalizationResponses() {
    }

    /** 语言。 */
    static LocaleResponseBody locale(I18nLocale row) {
        LocaleResponseBody body = new LocaleResponseBody();
        body.setLocaleCode(row.getLocaleCode());
        body.setNativeName(row.getNativeName());
        body.setEnglishName(row.getEnglishName());
        body.setTextDirection(row.getTextDirection().getCode());
        body.setStatus(row.getStatus().getCode());
        body.setSortOrder(row.getSortOrder());
        body.setVersion(row.getVersion());
        body.setUpdatedBy(Long.toString(row.getUpdatedBy()));
        body.setUpdateTime(time(row.getUpdateTime()));
        return body;
    }

    /**
     * 应用设置。
     *
     * @param row 应用
     * @param currentReleaseNo 当前发布号；没有时为 null
     * @param pendingChangeCount 未发布变更数
     * @return 响应体
     */
    static ApplicationResponseBody application(I18nApplication row, Integer currentReleaseNo, long pendingChangeCount) {
        ApplicationResponseBody body = new ApplicationResponseBody();
        body.setApplicationCode(row.getApplicationCode());
        body.setApplicationName(row.getApplicationName());
        body.setDefaultLocale(row.getDefaultLocale());
        body.setRequiredLocales(LocalizationJson.readLocales(row.getRequiredLocalesJson()));
        body.setCoverageThresholdPercent(row.getCoverageThresholdPercent());
        body.setStatus(row.getStatus().getCode());
        body.setCurrentReleaseNo(currentReleaseNo);
        body.setKeySetRevision(row.getKeySetRevision());
        body.setContentRevision(row.getContentRevision());
        body.setPendingChangeCount(pendingChangeCount);
        body.setVersion(row.getVersion());
        body.setUpdatedBy(Long.toString(row.getUpdatedBy()));
        body.setUpdateTime(time(row.getUpdateTime()));
        return body;
    }

    /** 命名空间与 key 数。 */
    static NamespaceResponseBody namespace(I18nNamespace row, long activeKeys, long deprecatedKeys) {
        NamespaceResponseBody body = new NamespaceResponseBody();
        body.setNamespaceCode(row.getNamespaceCode());
        body.setNamespaceName(row.getNamespaceName());
        body.setDescription(row.getDescription());
        body.setSortOrder(row.getSortOrder());
        body.setActiveKeyCount(activeKeys);
        body.setDeprecatedKeyCount(deprecatedKeys);
        body.setVersion(row.getVersion());
        body.setUpdatedBy(Long.toString(row.getUpdatedBy()));
        body.setUpdateTime(time(row.getUpdateTime()));
        return body;
    }

    /** 覆盖率。 */
    static LocaleCoverage coverage(LocaleCoverageFact fact) {
        LocaleCoverage body = new LocaleCoverage();
        body.setLocale(fact.getLocale());
        body.setRequired(fact.isRequired());
        body.setTotalKeys(fact.getTotalKeys());
        body.setTranslatedKeys(fact.getTranslatedKeys());
        body.setSourceChangedKeys(fact.getSourceChangedKeys());
        body.setCoveragePercent(fact.getCoveragePercent());
        body.setSatisfied(fact.isSatisfied());
        return body;
    }

    /** 覆盖率列表。 */
    static List<LocaleCoverage> coverage(List<LocaleCoverageFact> facts) {
        return facts.stream().map(LocalizationResponses::coverage).toList();
    }

    /** 语言与 key。 */
    static LocaleKey key(LocaleKeyRef ref) {
        LocaleKey body = new LocaleKey();
        body.setLocale(ref.locale());
        body.setKeyCode(ref.keyCode());
        return body;
    }

    /** 最多 limit 条语言与 key。 */
    static List<LocaleKey> keys(List<LocaleKeyRef> refs, int limit) {
        return refs.stream().limit(limit).map(LocalizationResponses::key).toList();
    }

    /** 校验问题。 */
    static com.paytech.platform.support.api.localization.response.LocalizationProblem problem(
        LocalizationProblem problem) {
        var body = new com.paytech.platform.support.api.localization.response.LocalizationProblem();
        body.setField(problem.field());
        body.setMessage(problem.message());
        return body;
    }

    /**
     * 发布摘要。
     *
     * @param row 发布
     * @param currentReleaseId 应用当前发布 ID；没有时为 null
     * @return 响应体
     */
    static ReleaseSummaryResponseBody release(I18nRelease row, Long currentReleaseId) {
        ReleaseSummaryResponseBody body = new ReleaseSummaryResponseBody();
        body.setReleaseNo(row.getReleaseNo());
        body.setStatus(row.getStatus().getCode());
        body.setCurrent(Objects.equals(row.getReleaseId(), currentReleaseId));
        body.setKeyCount(row.getKeyCount());
        body.setReleaseNote(row.getReleaseNote());
        body.setFailureReason(row.getFailureReason());
        body.setCreatedBy(Long.toString(row.getCreatedBy()));
        body.setCreateTime(time(row.getCreateTime()));
        body.setReadyTime(time(row.getReadyTime()));
        body.setChangeSummary(changeSummary(LocalizationJson.readReport(row.getReportJson()).getChangeSummary()));
        return body;
    }

    /** 变更摘要；null 为 null。 */
    static ReleaseChangeSummary changeSummary(ReleaseChangeSummaryFact fact) {
        if (fact == null) {
            return null;
        }
        ReleaseChangeSummary body = new ReleaseChangeSummary();
        body.setBaseReleaseNo(fact.getBaseReleaseNo());
        body.setAddedKeyCount(fact.getAddedKeyCount());
        body.setAddedKeys(fact.getAddedKeys());
        body.setDeprecatedKeyCount(fact.getDeprecatedKeyCount());
        body.setDeprecatedKeys(fact.getDeprecatedKeys());
        body.setSourceChangedKeyCount(fact.getSourceChangedKeyCount());
        body.setSourceChangedKeys(fact.getSourceChangedKeys());
        body.setTranslationChanges(fact.getTranslationChanges().stream().map(change -> {
            LocaleTranslationChange item = new LocaleTranslationChange();
            item.setLocale(change.getLocale());
            item.setChangedCount(change.getChangedCount());
            item.setSampleKeys(change.getSampleKeys());
            return item;
        }).toList());
        return body;
    }

    /** 语言包检查进度。 */
    static BundleCheckProgress checkProgress(List<BundleFileState> states) {
        BundleCheckProgress body = new BundleCheckProgress();
        body.setTotal(states.size());
        body.setAvailable((int) states.stream().filter(state -> state == BundleFileState.AVAILABLE).count());
        body.setPending((int) states.stream().filter(state -> state == BundleFileState.PENDING).count());
        body.setRejected((int) states.stream().filter(state -> state == BundleFileState.REJECTED).count());
        return body;
    }

    /** 翻译工作台条目。 */
    static TranslationEntryResponseBody entry(TranslationEntryView view) {
        TranslationEntryResponseBody body = new TranslationEntryResponseBody();
        body.setNamespaceCode(view.getNamespaceCode());
        body.setKeyCode(view.getKeyCode());
        body.setSourceText(view.getSourceText());
        body.setDescription(view.getDescription());
        body.setParameters(IcuMessageValidator.parameterNames(view.getSourceText()));
        body.setText(view.getText());
        body.setVersion(view.getTranslationVersion() == null ? 0 : view.getTranslationVersion());
        body.setSourceChanged(view.sourceChanged());
        body.setUpdatedBy(view.getUpdatedBy() == null ? null : Long.toString(view.getUpdatedBy()));
        body.setUpdateTime(time(view.getUpdateTime()));
        return body;
    }

    /** 语言包摘要。 */
    static ReleaseBundleSummary bundle(I18nReleaseBundle row) {
        ReleaseBundleSummary body = new ReleaseBundleSummary();
        body.setLocale(row.getLocaleCode());
        body.setNamespaceCode(row.getNamespaceCode());
        body.setFileAddress(row.getFileAddress());
        body.setContentHash(row.getContentHash());
        body.setByteSize(row.getByteSize());
        return body;
    }

    /** 文案变化。 */
    static ReleaseTextChange change(TextChange change) {
        ReleaseTextChange body = new ReleaseTextChange();
        body.setLocale(change.locale());
        body.setKeyCode(change.keyCode());
        body.setBefore(change.before());
        body.setAfter(change.after());
        return body;
    }

    /** UTC 微秒 ISO-8601；null 为 null。 */
    static String time(Instant instant) {
        return instant == null ? null : MICROS.format(instant);
    }
}
