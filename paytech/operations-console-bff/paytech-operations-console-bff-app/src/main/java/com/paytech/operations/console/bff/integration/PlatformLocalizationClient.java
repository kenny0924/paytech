package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
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

/**
 * Platform 多语言接口（/internal/localization/v1）的本地端口：23 个管理接口与匿名 manifest，每个入口只调用一次 Platform
 * <li>管理接口把会话 Account ID 与 Session ID 填入 X-Gateway-Actor-Subject / X-Gateway-Session-Id，Platform 再次校验权限。</li>
 * <li>Platform 的业务拒绝原因与多语言合同错误码（覆盖率不足、导入预览过期、文案已变、命名空间不存在）原样到达页面。</li>
 *
 * @author Claude
 */
public interface PlatformLocalizationClient {

    /**
     * 全部语言
     *
     * @param context 操作人
     * @return 按排序值、语言代码升序，含 DISABLED
     */
    LocaleListResponseBody listLocales(OperatorContext context);

    /**
     * 新增语言
     *
     * @param context 操作人
     * @param request 语言代码、名称、文字方向与排序
     * @return 新语言
     */
    LocaleResponseBody createLocale(OperatorContext context, CreateLocaleRequestBody request);

    /**
     * 修改语言
     *
     * @param context 操作人
     * @param localeCode 语言代码
     * @param request 全量新值与原版本
     * @return 修改后的语言，语言代码与路径一致
     */
    LocaleResponseBody updateLocale(OperatorContext context, String localeCode, UpdateLocaleRequestBody request);

    /**
     * 全部应用
     *
     * @param context 操作人
     * @return 按应用编码升序
     */
    ApplicationListResponseBody listApplications(OperatorContext context);

    /**
     * 应用详情
     *
     * @param context 操作人
     * @param applicationCode 应用编码
     * @return 设置、当前发布、key 数与覆盖率摘要，应用编码与路径一致
     */
    ApplicationDetailResponseBody getApplication(OperatorContext context, String applicationCode);

    /**
     * 新增应用
     *
     * @param context 操作人
     * @param request 编码、名称、默认语言、必须覆盖语言与阈值
     * @return 新应用，应用编码与请求一致
     */
    ApplicationResponseBody createApplication(OperatorContext context, CreateApplicationRequestBody request);

    /**
     * 修改应用
     *
     * @param context 操作人
     * @param applicationCode 应用编码
     * @param request 全量新值与原版本
     * @return 修改后的应用，应用编码与路径一致
     */
    ApplicationResponseBody updateApplication(OperatorContext context, String applicationCode,
        UpdateApplicationRequestBody request);

    /**
     * 应用的全部命名空间
     *
     * @param context 操作人
     * @param applicationCode 应用编码
     * @return 按排序值、编码升序
     */
    NamespaceListResponseBody listNamespaces(OperatorContext context, String applicationCode);

    /**
     * 新建命名空间
     *
     * @param context 操作人
     * @param applicationCode 应用编码
     * @param request 编码、名称、说明与排序
     * @return 新命名空间，编码与请求一致
     */
    NamespaceResponseBody createNamespace(OperatorContext context, String applicationCode,
        CreateNamespaceRequestBody request);

    /**
     * 修改命名空间
     *
     * @param context 操作人
     * @param applicationCode 应用编码
     * @param namespaceCode 命名空间编码
     * @param request 全量新值与原版本
     * @return 修改后的命名空间，编码与路径一致
     */
    NamespaceResponseBody updateNamespace(OperatorContext context, String applicationCode, String namespaceCode,
        UpdateNamespaceRequestBody request);

    /**
     * 删除命名空间（其下无任何 key 时）
     *
     * @param context 操作人
     * @param applicationCode 应用编码
     * @param namespaceCode 命名空间编码
     * @param expectedVersion 页面读取的原版本
     */
    void deleteNamespace(OperatorContext context, String applicationCode, String namespaceCode, Integer expectedVersion);

    /**
     * 预览 key 导入，不写库
     *
     * @param context 操作人
     * @param applicationCode 应用编码
     * @param request 已上传文件 ID
     * @return 四类差异、未创建命名空间、问题清单与预览摘要
     */
    KeyImportPreviewResponseBody previewKeyImport(OperatorContext context, String applicationCode,
        PreviewKeyImportRequestBody request);

    /**
     * 确认 key 导入
     *
     * @param context 操作人
     * @param applicationCode 应用编码
     * @param request 文件 ID 与预览摘要
     * @return 各类数量与新的修订号
     */
    KeyImportResultResponseBody confirmKeyImport(OperatorContext context, String applicationCode,
        ConfirmKeyImportRequestBody request);

    /**
     * 翻译工作台查询
     *
     * @param context 操作人
     * @param applicationCode 应用编码
     * @param locale 目标语言代码
     * @param namespaceCode 可选命名空间
     * @param state 可选填写状态
     * @param keyword 可选关键字
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 分页结果，页码与条数与请求一致
     */
    PagingResponseBody<TranslationEntryResponseBody> listTranslations(OperatorContext context, String applicationCode,
        String locale, String namespaceCode, String state, String keyword, int page, int size);

    /**
     * 批量保存一个语言的翻译，全部成功或全部失败
     *
     * @param context 操作人
     * @param applicationCode 应用编码
     * @param request 语言与翻译条目
     * @return 各条新版本与新的文案修订号
     */
    SaveTranslationsResponseBody saveTranslations(OperatorContext context, String applicationCode,
        SaveTranslationsRequestBody request);

    /**
     * 导出一个语言的翻译
     *
     * @param context 操作人
     * @param applicationCode 应用编码
     * @param locale 语言代码
     * @return 翻译文件，应用与语言与请求一致
     */
    TranslationFileResponseBody exportTranslations(OperatorContext context, String applicationCode, String locale);

    /**
     * 导入一个语言的翻译文件
     *
     * @param context 操作人
     * @param applicationCode 应用编码
     * @param request 文件 ID 与语言
     * @return 写入、未变化与空值条数
     */
    ImportTranslationsResponseBody importTranslations(OperatorContext context, String applicationCode,
        ImportTranslationsRequestBody request);

    /**
     * 发布预检，只读
     *
     * @param context 操作人
     * @param applicationCode 应用编码
     * @return 覆盖率、是否可发布与问题清单
     */
    ReleasePrecheckResponseBody precheckRelease(OperatorContext context, String applicationCode);

    /**
     * 发布当前文案
     *
     * @param context 操作人
     * @param applicationCode 应用编码
     * @param request 发布说明
     * @return 新发布摘要（PENDING_CHECK）
     */
    ReleaseSummaryResponseBody publishRelease(OperatorContext context, String applicationCode,
        PublishReleaseRequestBody request);

    /**
     * 发布历史
     *
     * @param context 操作人
     * @param applicationCode 应用编码
     * @param page 已校验页码
     * @param size 已校验每页条数
     * @return 分页结果，页码与条数与请求一致
     */
    PagingResponseBody<ReleaseSummaryResponseBody> listReleases(OperatorContext context, String applicationCode,
        int page, int size);

    /**
     * 发布详情
     *
     * @param context 操作人
     * @param applicationCode 应用编码
     * @param releaseNo 发布号
     * @return 摘要、覆盖率、报告与语言包，发布号与路径一致
     */
    ReleaseDetailResponseBody getRelease(OperatorContext context, String applicationCode, Integer releaseNo);

    /**
     * 两次发布的差异
     *
     * @param context 操作人
     * @param applicationCode 应用编码
     * @param releaseNo 对比的发布号
     * @param baseReleaseNo 可选基准发布号
     * @return 按语言与 key 的新增、变化与删除，发布号与路径一致
     */
    ReleaseDiffResponseBody diffRelease(OperatorContext context, String applicationCode, Integer releaseNo,
        Integer baseReleaseNo);

    /**
     * 切换应用当前发布
     *
     * @param context 操作人
     * @param applicationCode 应用编码
     * @param request 目标发布号与原因
     * @return 切换后的当前发布号，与请求一致
     */
    CurrentReleaseResponseBody activateRelease(OperatorContext context, String applicationCode,
        ActivateReleaseRequestBody request);

    /**
     * 匿名读取本控制台应用的 manifest；应用编码由 BFF 固定
     *
     * @param applicationCode BFF 固定的应用编码
     * @param locale 可选目标语言
     * @param releaseNo 可选指定发布号（预览）
     * @return manifest，应用编码与请求一致
     */
    ManifestResponseBody getManifest(String applicationCode, String locale, Integer releaseNo);
}
