package com.paytech.platform.support.ext.rest.localization;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.localization.LocalizationContentApi;
import com.paytech.platform.support.api.localization.request.ConfirmKeyImportRequestBody;
import com.paytech.platform.support.api.localization.request.ImportTranslationsRequestBody;
import com.paytech.platform.support.api.localization.request.PreviewKeyImportRequestBody;
import com.paytech.platform.support.api.localization.request.SaveTranslationsRequestBody;
import com.paytech.platform.support.api.localization.response.ImportTranslationsResponseBody;
import com.paytech.platform.support.api.localization.response.KeyImportPreviewResponseBody;
import com.paytech.platform.support.api.localization.response.KeyImportResultResponseBody;
import com.paytech.platform.support.api.localization.response.SaveTranslationsResponseBody;
import com.paytech.platform.support.api.localization.response.TranslationEntryResponseBody;
import com.paytech.platform.support.api.localization.response.TranslationFileResponseBody;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.localization.service.processor.ConfirmKeyImportProcessor;
import com.paytech.platform.support.localization.service.processor.ImportTranslationsProcessor;
import com.paytech.platform.support.localization.service.processor.ExportTranslationsProcessor;
import com.paytech.platform.support.localization.service.processor.PreviewKeyImportProcessor;
import com.paytech.platform.support.localization.service.processor.ListTranslationsProcessor;
import com.paytech.platform.support.localization.service.processor.SaveTranslationsProcessor;
import com.paytech.platform.support.localization.service.processor.request.ImportTranslationsCommand;
import com.paytech.platform.support.localization.service.processor.request.KeyImportCommand;
import com.paytech.platform.support.localization.service.processor.request.TranslationExportQuery;
import com.paytech.platform.support.localization.service.processor.request.TranslationListQuery;

import lombok.AllArgsConstructor;

/** key 导入与翻译维护的协议适配；文件读取、校验、锁与事务委托 localization 模块的 Processor。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class LocalizationContentController implements LocalizationContentApi {

    private final LocalizationConverter converter;
    private final ListTranslationsProcessor listProcessor;
    private final ExportTranslationsProcessor exportProcessor;
    private final SaveTranslationsProcessor saveProcessor;
    private final PreviewKeyImportProcessor previewProcessor;
    private final ConfirmKeyImportProcessor confirmProcessor;
    private final ImportTranslationsProcessor importProcessor;

    /** 导入预览。 */
    @Override
    public KeyImportPreviewResponseBody previewKeyImport(String actor, String session, String applicationCode,
        PreviewKeyImportRequestBody request) {
        return previewProcessor.process(new KeyImportCommand(actor, session, applicationCode,
            LocalizationConverter.id(request.getFileId(), "fileId"), null));
    }

    /** 确认导入。 */
    @Audit(action = "I18N_KEY_IMPORT", resourceType = "I18N_APPLICATION", fallbackResourceId = "I18N_APPLICATION_CATALOG")
    @Override
    public KeyImportResultResponseBody confirmKeyImport(String actor, String session, String applicationCode,
        ConfirmKeyImportRequestBody request) {
        return confirmProcessor.process(new KeyImportCommand(actor, session, applicationCode,
            LocalizationConverter.id(request.getFileId(), "fileId"), request.getPreviewDigest()));
    }

    /** 翻译工作台。 */
    @Override
    public PagingResponseBody<TranslationEntryResponseBody> listTranslations(String actor, String session,
        String applicationCode, String locale, String namespaceCode, String state, String keyword, Integer page,
        Integer size) {
        return listProcessor.process(new TranslationListQuery(actor, session, applicationCode, locale,
            LocalizationConverter.text(namespaceCode), converter.state(state), LocalizationConverter.keyword(keyword),
            PagingRequestBody.of(page, size)));
    }

    /** 批量保存。 */
    @Audit(action = "I18N_TRANSLATION_SAVE", resourceType = "I18N_APPLICATION", fallbackResourceId = "I18N_APPLICATION_CATALOG")
    @Override
    public SaveTranslationsResponseBody saveTranslations(String actor, String session, String applicationCode,
        SaveTranslationsRequestBody request) {
        return saveProcessor.process(converter.saveTranslations(actor, session, applicationCode, request));
    }

    /** 导出。 */
    @Override
    public TranslationFileResponseBody exportTranslations(String actor, String session, String applicationCode,
        String locale) {
        return exportProcessor.process(new TranslationExportQuery(actor, session, applicationCode, locale));
    }

    /** 导入翻译。 */
    @Audit(action = "I18N_TRANSLATION_IMPORT", resourceType = "I18N_APPLICATION", fallbackResourceId = "I18N_APPLICATION_CATALOG")
    @Override
    public ImportTranslationsResponseBody importTranslations(String actor, String session, String applicationCode,
        ImportTranslationsRequestBody request) {
        return importProcessor.process(new ImportTranslationsCommand(actor, session, applicationCode,
            request.getLocale(), LocalizationConverter.id(request.getFileId(), "fileId")));
    }
}
