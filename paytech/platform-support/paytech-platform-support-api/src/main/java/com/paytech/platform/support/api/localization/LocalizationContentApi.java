package com.paytech.platform.support.api.localization;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
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

import jakarta.validation.Valid;

/**
 * 多语言文案维护：key 导入与翻译；只供 operations-console-bff 私网调用，操作人由 Gateway 可信 Header 传递。
 * <li>查询与导出需要 I18N_VIEW，导入与保存需要 I18N_EDIT。</li>
 * <li>导入文件经既有文件上传链路上传（原因码 I18N_KEY_IMPORT / I18N_TRANSLATION_IMPORT，application/json，最大 2 MB），这里只传文件
 * ID；仍在检查返回 409 I18N_IMPORT_FILE_NOT_READY；文件不存在、原因码不符、检查被拒绝或隔离、对象不可用或过大返回 400。</li>
 * <li>每次实际写入使应用的 contentRevision 加一；同一应用的写操作串行，正在执行时返回 409。</li>
 */
@HttpExchange(url = "/internal/localization/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface LocalizationContentApi {

    /**
     * 预览 key 导入，不写库
     * <li>文件格式 code-mor-i18n-source/1：{format, application, namespaces: {命名空间: {完整 key: 文案 | {text, description}}}}；
     * 文件代表应用完整的 key 集合。</li>
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param applicationCode 应用编码，须与文件中的 application 一致
     * @param request 文件 ID
     * @return 新增 / 基准变更 / 将废弃 / 恢复在用四类清单、未创建的命名空间、格式 / 前缀 / ICU 问题与 previewDigest
     */
    @PostExchange("/applications/{applicationCode}/key-imports/previews")
    KeyImportPreviewResponseBody previewKeyImport(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("applicationCode") String applicationCode,
        @Valid @RequestBody PreviewKeyImportRequestBody request);

    /**
     * 确认 key 导入，一个事务内生效
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param applicationCode 应用编码
     * @param request 文件 ID 与预览摘要
     * @return 各类数量与新的修订号；keySetRevision 与 contentRevision 各加一
     *         <li>预览后有人导入过返回 409 I18N_IMPORT_PREVIEW_STALE；仍有未创建的命名空间返回 422 I18N_NAMESPACE_NOT_FOUND，errors
     *         的 field 为 namespaces.{编码}；格式、前缀或 ICU 问题返回 422 VALIDATION_ERROR。</li>
     */
    @PostExchange("/applications/{applicationCode}/key-imports")
    KeyImportResultResponseBody confirmKeyImport(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("applicationCode") String applicationCode,
        @Valid @RequestBody ConfirmKeyImportRequestBody request);

    /**
     * 翻译工作台查询：ACTIVE key 与目标语言翻译
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param applicationCode 应用编码
     * @param locale 目标语言代码，ENABLED 且不是 en
     * @param namespaceCode 可选：命名空间编码
     * @param state 可选：UNTRANSLATED（未翻译）、SOURCE_CHANGED（基准已变更）、TRANSLATED（已翻译且基准未变）
     * @param keyword 可选：key、英文基准或翻译包含该文本，最多 64 字符
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按命名空间排序值、key 升序
     */
    @GetExchange("/applications/{applicationCode}/translations")
    PagingResponseBody<TranslationEntryResponseBody> listTranslations(
        @RequestHeader("X-Gateway-Actor-Subject") String actor, @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("applicationCode") String applicationCode, @RequestParam("locale") String locale,
        @RequestParam(value = "namespaceCode", required = false) String namespaceCode,
        @RequestParam(value = "state", required = false) String state,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 批量保存一个语言的翻译，全部成功或全部失败
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param applicationCode 应用编码
     * @param request 语言与最多 200 条翻译
     * @return 各条新版本与新的文案修订号；保存后翻译依据当前基准（不再是基准已变更）
     *         <li>key 不存在或已废弃返回 422 VALIDATION_ERROR，field 为 translations[i].keyCode；ICU 校验失败返回 422
     *         I18N_MESSAGE_INVALID，field 为 translations[i].text，消息带字符位置；任一条版本不符返回 409。</li>
     */
    @PostExchange("/applications/{applicationCode}/translations/batch")
    SaveTranslationsResponseBody saveTranslations(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("applicationCode") String applicationCode,
        @Valid @RequestBody SaveTranslationsRequestBody request);

    /**
     * 导出一个语言的翻译
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param applicationCode 应用编码
     * @param locale 语言代码，ENABLED 且不是 en
     * @return 翻译文件；原样导入不产生任何写入
     */
    @GetExchange("/applications/{applicationCode}/translations/export")
    TranslationFileResponseBody exportTranslations(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("applicationCode") String applicationCode,
        @RequestParam("locale") String locale);

    /**
     * 导入一个语言的翻译文件：全量校验通过后整体写入
     * <li>结构同导出；空字符串表示不改动，与现有翻译相同的条目不写入。</li>
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param applicationCode 应用编码
     * @param request 文件 ID 与语言
     * @return 写入、未变化与空值的条数
     *         <li>任一条失败整体拒绝，errors 最多 200 条，field 为 namespaces.{命名空间}.{key}：格式、未知 / 已废弃 key 返回 422
     *         VALIDATION_ERROR；ICU 校验失败返回 422 I18N_MESSAGE_INVALID，消息带字符位置。</li>
     */
    @PostExchange("/applications/{applicationCode}/translations/imports")
    ImportTranslationsResponseBody importTranslations(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("applicationCode") String applicationCode,
        @Valid @RequestBody ImportTranslationsRequestBody request);
}
