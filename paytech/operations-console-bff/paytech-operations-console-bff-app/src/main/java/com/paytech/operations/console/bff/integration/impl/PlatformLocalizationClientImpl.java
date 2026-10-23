package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.require;
import static com.paytech.operations.console.bff.integration.impl.LocalizationValidationConverter.importFile;
import static com.paytech.operations.console.bff.integration.impl.LocalizationValidationConverter.write;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformLocalizationClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.platform.support.api.localization.LocalizationCatalogApi;
import com.paytech.platform.support.api.localization.LocalizationContentApi;
import com.paytech.platform.support.api.localization.LocalizationDeliveryApi;
import com.paytech.platform.support.api.localization.LocalizationReleaseApi;
import com.paytech.platform.support.api.localization.request.ActivateReleaseRequestBody;
import com.paytech.platform.support.api.localization.request.ConfirmKeyImportRequestBody;
import com.paytech.platform.support.api.localization.request.CreateApplicationRequestBody;
import com.paytech.platform.support.api.localization.request.CreateLocaleRequestBody;
import com.paytech.platform.support.api.localization.request.CreateNamespaceRequestBody;
import com.paytech.platform.support.api.localization.request.ImportTranslationsRequestBody;
import com.paytech.platform.support.api.localization.request.PreviewKeyImportRequestBody;
import com.paytech.platform.support.api.localization.request.PublishReleaseRequestBody;
import com.paytech.platform.support.api.localization.request.SaveTranslationsRequestBody;
import com.paytech.platform.support.api.localization.request.UpdateApplicationRequestBody;
import com.paytech.platform.support.api.localization.request.UpdateLocaleRequestBody;
import com.paytech.platform.support.api.localization.request.UpdateNamespaceRequestBody;
import com.paytech.platform.support.api.localization.response.ApplicationDetailResponseBody;
import com.paytech.platform.support.api.localization.response.ApplicationListResponseBody;
import com.paytech.platform.support.api.localization.response.ApplicationResponseBody;
import com.paytech.platform.support.api.localization.response.CurrentReleaseResponseBody;
import com.paytech.platform.support.api.localization.response.ImportTranslationsResponseBody;
import com.paytech.platform.support.api.localization.response.KeyImportPreviewResponseBody;
import com.paytech.platform.support.api.localization.response.KeyImportResultResponseBody;
import com.paytech.platform.support.api.localization.response.LocaleListResponseBody;
import com.paytech.platform.support.api.localization.response.LocaleResponseBody;
import com.paytech.platform.support.api.localization.response.ManifestResponseBody;
import com.paytech.platform.support.api.localization.response.NamespaceListResponseBody;
import com.paytech.platform.support.api.localization.response.NamespaceResponseBody;
import com.paytech.platform.support.api.localization.response.ReleaseDetailResponseBody;
import com.paytech.platform.support.api.localization.response.ReleaseDiffResponseBody;
import com.paytech.platform.support.api.localization.response.ReleasePrecheckResponseBody;
import com.paytech.platform.support.api.localization.response.ReleaseSummaryResponseBody;
import com.paytech.platform.support.api.localization.response.SaveTranslationsResponseBody;
import com.paytech.platform.support.api.localization.response.TranslationEntryResponseBody;
import com.paytech.platform.support.api.localization.response.TranslationFileResponseBody;

import lombok.AllArgsConstructor;

/**
 * Platform 多语言接口的 HTTP 转发：每个入口只调用一次 Platform，不重试，请求幂等由 Gateway 负责
 * <li>回执只核对与本次请求对应的身份（路径中的语言、应用、命名空间、发布号与分页窗口），不符视为上游无效响应 502。</li>
 * <li>写接口的 422 字段路径按本次 Body 投影；翻译文件导入按文件内路径形态投影。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class PlatformLocalizationClientImpl implements PlatformLocalizationClient {
    private final LocalizationCatalogApi catalog;
    private final LocalizationContentApi content;
    private final LocalizationReleaseApi releases;
    private final LocalizationDeliveryApi delivery;

    @Override
    public LocaleListResponseBody listLocales(OperatorContext context) {
        var result = catalog.listLocales(context.accountId(), context.sessionId());
        require(items(result.getItems()));
        return result;
    }

    @Override
    public LocaleResponseBody createLocale(OperatorContext context, CreateLocaleRequestBody request) {
        var result = write(request, () -> catalog.createLocale(context.accountId(), context.sessionId(), request));
        require(Objects.equals(request.getLocaleCode(), result.getLocaleCode()));
        return result;
    }

    @Override
    public LocaleResponseBody updateLocale(OperatorContext context, String localeCode, UpdateLocaleRequestBody request) {
        var result = write(request, () -> catalog.updateLocale(context.accountId(), context.sessionId(), localeCode,
            request));
        require(Objects.equals(localeCode, result.getLocaleCode()));
        return result;
    }

    @Override
    public ApplicationListResponseBody listApplications(OperatorContext context) {
        var result = catalog.listApplications(context.accountId(), context.sessionId());
        require(items(result.getItems()));
        return result;
    }

    @Override
    public ApplicationDetailResponseBody getApplication(OperatorContext context, String applicationCode) {
        var result = catalog.getApplication(context.accountId(), context.sessionId(), applicationCode);
        require(result.getApplication() != null
            && Objects.equals(applicationCode, result.getApplication().getApplicationCode()));
        return result;
    }

    @Override
    public ApplicationResponseBody createApplication(OperatorContext context, CreateApplicationRequestBody request) {
        var result = write(request, () -> catalog.createApplication(context.accountId(), context.sessionId(), request));
        require(Objects.equals(request.getApplicationCode(), result.getApplicationCode()));
        return result;
    }

    @Override
    public ApplicationResponseBody updateApplication(OperatorContext context, String applicationCode,
        UpdateApplicationRequestBody request) {
        var result = write(request, () -> catalog.updateApplication(context.accountId(), context.sessionId(),
            applicationCode, request));
        require(Objects.equals(applicationCode, result.getApplicationCode()));
        return result;
    }

    @Override
    public NamespaceListResponseBody listNamespaces(OperatorContext context, String applicationCode) {
        var result = catalog.listNamespaces(context.accountId(), context.sessionId(), applicationCode);
        require(items(result.getItems()));
        return result;
    }

    @Override
    public NamespaceResponseBody createNamespace(OperatorContext context, String applicationCode,
        CreateNamespaceRequestBody request) {
        var result = write(request, () -> catalog.createNamespace(context.accountId(), context.sessionId(),
            applicationCode, request));
        require(Objects.equals(request.getNamespaceCode(), result.getNamespaceCode()));
        return result;
    }

    @Override
    public NamespaceResponseBody updateNamespace(OperatorContext context, String applicationCode, String namespaceCode,
        UpdateNamespaceRequestBody request) {
        var result = write(request, () -> catalog.updateNamespace(context.accountId(), context.sessionId(),
            applicationCode, namespaceCode, request));
        require(Objects.equals(namespaceCode, result.getNamespaceCode()));
        return result;
    }

    @Override
    public void deleteNamespace(OperatorContext context, String applicationCode, String namespaceCode,
        Integer expectedVersion) {
        catalog.deleteNamespace(context.accountId(), context.sessionId(), applicationCode, namespaceCode, expectedVersion);
    }

    @Override
    public KeyImportPreviewResponseBody previewKeyImport(OperatorContext context, String applicationCode,
        PreviewKeyImportRequestBody request) {
        return write(request, () -> content.previewKeyImport(context.accountId(), context.sessionId(), applicationCode,
            request));
    }

    @Override
    public KeyImportResultResponseBody confirmKeyImport(OperatorContext context, String applicationCode,
        ConfirmKeyImportRequestBody request) {
        return write(request, () -> content.confirmKeyImport(context.accountId(), context.sessionId(), applicationCode,
            request));
    }

    @Override
    public PagingResponseBody<TranslationEntryResponseBody> listTranslations(OperatorContext context,
        String applicationCode, String locale, String namespaceCode, String state, String keyword, int page, int size) {
        return page(content.listTranslations(context.accountId(), context.sessionId(), applicationCode, locale,
            namespaceCode, state, keyword, page, size), page, size);
    }

    @Override
    public SaveTranslationsResponseBody saveTranslations(OperatorContext context, String applicationCode,
        SaveTranslationsRequestBody request) {
        return write(request, () -> content.saveTranslations(context.accountId(), context.sessionId(), applicationCode,
            request));
    }

    @Override
    public TranslationFileResponseBody exportTranslations(OperatorContext context, String applicationCode,
        String locale) {
        var result = content.exportTranslations(context.accountId(), context.sessionId(), applicationCode, locale);
        require(Objects.equals(applicationCode, result.getApplication()) && Objects.equals(locale, result.getLocale()));
        return result;
    }

    @Override
    public ImportTranslationsResponseBody importTranslations(OperatorContext context, String applicationCode,
        ImportTranslationsRequestBody request) {
        return importFile(() -> content.importTranslations(context.accountId(), context.sessionId(), applicationCode,
            request));
    }

    @Override
    public ReleasePrecheckResponseBody precheckRelease(OperatorContext context, String applicationCode) {
        return releases.precheckRelease(context.accountId(), context.sessionId(), applicationCode);
    }

    @Override
    public ReleaseSummaryResponseBody publishRelease(OperatorContext context, String applicationCode,
        PublishReleaseRequestBody request) {
        return write(request, () -> releases.publishRelease(context.accountId(), context.sessionId(), applicationCode,
            request));
    }

    @Override
    public PagingResponseBody<ReleaseSummaryResponseBody> listReleases(OperatorContext context, String applicationCode,
        int page, int size) {
        return page(releases.listReleases(context.accountId(), context.sessionId(), applicationCode, page, size), page,
            size);
    }

    @Override
    public ReleaseDetailResponseBody getRelease(OperatorContext context, String applicationCode, Integer releaseNo) {
        var result = releases.getRelease(context.accountId(), context.sessionId(), applicationCode, releaseNo);
        require(result.getRelease() != null && result.getRelease().getReleaseNo() == releaseNo);
        return result;
    }

    @Override
    public ReleaseDiffResponseBody diffRelease(OperatorContext context, String applicationCode, Integer releaseNo,
        Integer baseReleaseNo) {
        var result = releases.diffRelease(context.accountId(), context.sessionId(), applicationCode, releaseNo,
            baseReleaseNo);
        require(result.getReleaseNo() == releaseNo
            && (baseReleaseNo == null || Objects.equals(baseReleaseNo, result.getBaseReleaseNo())));
        return result;
    }

    @Override
    public CurrentReleaseResponseBody activateRelease(OperatorContext context, String applicationCode,
        ActivateReleaseRequestBody request) {
        var result = write(request, () -> releases.activateRelease(context.accountId(), context.sessionId(),
            applicationCode, request));
        require(Objects.equals(applicationCode, result.getApplicationCode())
            && result.getReleaseNo() == request.getReleaseNo());
        return result;
    }

    @Override
    public ManifestResponseBody getManifest(String applicationCode, String locale, Integer releaseNo) {
        var result = delivery.getManifest(applicationCode, locale, releaseNo);
        require(Objects.equals(applicationCode, result.getApplication()) && items(result.getLocales())
            && result.getBundles() != null && result.getBundles().values().stream().allMatch(Objects::nonNull)
            && (releaseNo == null || Objects.equals(releaseNo, result.getReleaseNo())));
        return result;
    }

    /** 列表回执须带列表且不含空元素。 */
    private static boolean items(List<?> items) {
        return items != null && items.stream().allMatch(Objects::nonNull);
    }

    /** 分页回执的页码、条数与请求一致，元素非空且不超过每页条数。 */
    private static <T> PagingResponseBody<T> page(PagingResponseBody<T> result, int page, int size) {
        require(result.page() == page && result.size() == size && result.total() >= 0 && result.items().size() <= size
            && result.items().stream().allMatch(Objects::nonNull));
        return result;
    }
}
