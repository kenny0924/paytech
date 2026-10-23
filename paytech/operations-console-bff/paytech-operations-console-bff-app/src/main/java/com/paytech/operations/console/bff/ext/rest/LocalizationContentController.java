package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.LocalizationPaths.APPLICATION;
import static com.paytech.operations.console.bff.ext.rest.LocalizationPaths.ID;
import static com.paytech.operations.console.bff.ext.rest.LocalizationPaths.LOCALE;
import static com.paytech.operations.console.bff.ext.rest.LocalizationPaths.NAMESPACE;
import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.LocalizationFileClient;
import com.paytech.operations.console.bff.integration.PlatformLocalizationClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.localization.request.StartLocalizationUploadRequestBody;
import com.paytech.operations.console.bff.service.model.localization.response.LocalizationUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;

/**
 * 多语言「翻译」页的浏览器入口：key 导入 2、翻译 4 转发 Platform 同路径，导入文件上传 2 转发 Platform 内部文件接口
 * <li>Gateway 绑定：工作台查询与导出 I18N_VIEW；上传、导入预览 / 确认、保存与翻译导入 I18N_EDIT，并经 PLT-I02 实时授权。</li>
 * <li>导入文件由浏览器按 PUT 授权直传对象存储，不经 Gateway；上传开始的响应含临时授权，上传路径只记元数据日志。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping(LocalizationPaths.ADMIN)
public class LocalizationContentController {
    private static final Set<String> TRANSLATION_QUERY = Set.of("locale", "namespaceCode", "state", "keyword", "page",
        "size");

    private final LocalizationFileClient fileClient;
    private final PlatformLocalizationClient client;

    /** 开始上传导入文件，返回浏览器直传的临时 PUT 授权（201）。 */
    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public FileUploadResponseBody startUpload(HttpServletRequest servletRequest, OperatorContext context,
        @RequestBody @NotNull @Valid StartLocalizationUploadRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return fileClient.startUpload(context, body);
    }

    /** 完成上传，返回文件 ID、地址与扫描状态；不接受 Body。 */
    @PostMapping("/uploads/{uploadId}/completion")
    public LocalizationUploadCompletionResponseBody completeUpload(HttpServletRequest servletRequest,
        @PathVariable("uploadId") @Pattern(regexp = ID) String uploadId) {
        requireQueryKeys(servletRequest, Set.of());
        return fileClient.completeUpload(uploadId);
    }

    /** 预览 key 导入，不写库。 */
    @PostMapping("/applications/{applicationCode}/key-imports/previews")
    public KeyImportPreviewResponseBody previewKeyImport(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("applicationCode") @Pattern(regexp = APPLICATION) String applicationCode,
        @RequestBody @NotNull @Valid PreviewKeyImportRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.previewKeyImport(context, applicationCode, body);
    }

    /** 确认 key 导入；预览过期 409、命名空间未创建 422 的错误码原样返回。 */
    @PostMapping("/applications/{applicationCode}/key-imports")
    public KeyImportResultResponseBody confirmKeyImport(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("applicationCode") @Pattern(regexp = APPLICATION) String applicationCode,
        @RequestBody @NotNull @Valid ConfirmKeyImportRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.confirmKeyImport(context, applicationCode, body);
    }

    /** 翻译工作台分页查询。 */
    @GetMapping("/applications/{applicationCode}/translations")
    public PagingResponseBody<TranslationEntryResponseBody> listTranslations(HttpServletRequest servletRequest,
        OperatorContext context, @PathVariable("applicationCode") @Pattern(regexp = APPLICATION) String applicationCode,
        @RequestParam("locale") @Pattern(regexp = LOCALE) String locale,
        @RequestParam(value = "namespaceCode", required = false) @Pattern(regexp = NAMESPACE) String namespaceCode,
        @RequestParam(value = "state", required = false) @Pattern(regexp = "UNTRANSLATED|SOURCE_CHANGED|TRANSLATED") String state,
        @RequestParam(value = "keyword", required = false) @Size(max = 64) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, TRANSLATION_QUERY);
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listTranslations(context, applicationCode, locale, namespaceCode, state, keyword, paging.page(),
            paging.size());
    }

    /** 批量保存一个语言的翻译，全部成功或全部失败。 */
    @PostMapping("/applications/{applicationCode}/translations/batch")
    public SaveTranslationsResponseBody saveTranslations(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("applicationCode") @Pattern(regexp = APPLICATION) String applicationCode,
        @RequestBody @NotNull @Valid SaveTranslationsRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.saveTranslations(context, applicationCode, body);
    }

    /** 导出一个语言的翻译文件。 */
    @GetMapping("/applications/{applicationCode}/translations/export")
    public TranslationFileResponseBody exportTranslations(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("applicationCode") @Pattern(regexp = APPLICATION) String applicationCode,
        @RequestParam("locale") @Pattern(regexp = LOCALE) String locale) {
        requireQueryKeys(servletRequest, Set.of("locale"));
        return client.exportTranslations(context, applicationCode, locale);
    }

    /** 导入一个语言的翻译文件，全量校验后整体写入。 */
    @PostMapping("/applications/{applicationCode}/translations/imports")
    public ImportTranslationsResponseBody importTranslations(HttpServletRequest servletRequest, OperatorContext context,
        @PathVariable("applicationCode") @Pattern(regexp = APPLICATION) String applicationCode,
        @RequestBody @NotNull @Valid ImportTranslationsRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.importTranslations(context, applicationCode, body);
    }
}
