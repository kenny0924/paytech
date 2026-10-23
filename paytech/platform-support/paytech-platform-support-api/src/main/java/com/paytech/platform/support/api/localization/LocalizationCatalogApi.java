package com.paytech.platform.support.api.localization;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.platform.support.api.localization.request.CreateApplicationRequestBody;
import com.paytech.platform.support.api.localization.request.CreateLocaleRequestBody;
import com.paytech.platform.support.api.localization.request.CreateNamespaceRequestBody;
import com.paytech.platform.support.api.localization.request.UpdateApplicationRequestBody;
import com.paytech.platform.support.api.localization.request.UpdateLocaleRequestBody;
import com.paytech.platform.support.api.localization.request.UpdateNamespaceRequestBody;
import com.paytech.platform.support.api.localization.response.ApplicationDetailResponseBody;
import com.paytech.platform.support.api.localization.response.ApplicationListResponseBody;
import com.paytech.platform.support.api.localization.response.ApplicationResponseBody;
import com.paytech.platform.support.api.localization.response.LocaleListResponseBody;
import com.paytech.platform.support.api.localization.response.LocaleResponseBody;
import com.paytech.platform.support.api.localization.response.NamespaceListResponseBody;
import com.paytech.platform.support.api.localization.response.NamespaceResponseBody;

import jakarta.validation.Valid;

/**
 * 多语言目录维护：语言、应用与命名空间；只供 operations-console-bff 私网调用，操作人由 Gateway 可信 Header 传递。
 * <li>查询需要 I18N_VIEW，写操作需要 I18N_EDIT；无权限返回 403 OPERATIONS_PERMISSION_DENIED，会话无效返回 401。</li>
 * <li>写接口带 expectedVersion，版本不符返回 409 CONCURRENT_REQUEST；同一应用（语言按语言代码）的写操作串行，正在执行时返回 409。</li>
 * <li>应用或命名空间不存在返回 404 NOT_FOUND；业务规则不满足返回 400 BUSINESS_CHECK_FAILED。</li>
 */
@HttpExchange(url = "/internal/localization/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface LocalizationCatalogApi {

    /**
     * 全部语言
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @return 按排序值、语言代码升序；含 DISABLED
     */
    @GetExchange("/locales")
    LocaleListResponseBody listLocales(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session);

    /**
     * 新增语言，状态为 ENABLED，版本为 1
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param request 语言代码、名称、文字方向与排序
     * @return 新语言
     *         <li>语言代码已存在返回 400。</li>
     */
    @PostExchange("/locales")
    LocaleResponseBody createLocale(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @Valid @RequestBody CreateLocaleRequestBody request);

    /**
     * 修改语言名称、文字方向、状态与排序，版本加 1
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param localeCode 语言代码
     * @param request 全量新值与原版本
     * @return 修改后的语言；与当前完全相同时不递增版本
     *         <li>停用 en，或停用被应用用作默认语言、必须覆盖语言的语言，返回 400。</li>
     */
    @PutExchange("/locales/{localeCode}")
    LocaleResponseBody updateLocale(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("localeCode") String localeCode,
        @Valid @RequestBody UpdateLocaleRequestBody request);

    /**
     * 全部应用
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @return 按应用编码升序
     */
    @GetExchange("/applications")
    ApplicationListResponseBody listApplications(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session);

    /**
     * 应用详情
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param applicationCode 应用编码
     * @return 设置、当前发布、key 数与各语言覆盖率摘要
     */
    @GetExchange("/applications/{applicationCode}")
    ApplicationDetailResponseBody getApplication(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("applicationCode") String applicationCode);

    /**
     * 新增应用，状态为 ENABLED，版本为 1，尚无发布
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param request 编码、名称、默认语言、必须覆盖语言与阈值
     * @return 新应用
     *         <li>编码已存在、语言不存在或未启用、必须覆盖语言重复返回 400。</li>
     */
    @PostExchange("/applications")
    ApplicationResponseBody createApplication(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @Valid @RequestBody CreateApplicationRequestBody request);

    /**
     * 修改应用名称、默认语言、必须覆盖语言与阈值，版本加 1
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param applicationCode 应用编码
     * @param request 全量新值与原版本
     * @return 修改后的应用；与当前完全相同时不递增版本
     */
    @PutExchange("/applications/{applicationCode}")
    ApplicationResponseBody updateApplication(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("applicationCode") String applicationCode,
        @Valid @RequestBody UpdateApplicationRequestBody request);

    /**
     * 应用的全部命名空间
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param applicationCode 应用编码
     * @return 按排序值、编码升序，含在用与已废弃 key 数
     */
    @GetExchange("/applications/{applicationCode}/namespaces")
    NamespaceListResponseBody listNamespaces(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("applicationCode") String applicationCode);

    /**
     * 新建命名空间，版本为 1
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param applicationCode 应用编码
     * @param request 编码、名称、说明与排序
     * @return 新命名空间
     *         <li>应用内编码已存在返回 400。</li>
     */
    @PostExchange("/applications/{applicationCode}/namespaces")
    NamespaceResponseBody createNamespace(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("applicationCode") String applicationCode,
        @Valid @RequestBody CreateNamespaceRequestBody request);

    /**
     * 修改命名空间名称、说明与排序，版本加 1
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param applicationCode 应用编码
     * @param namespaceCode 命名空间编码
     * @param request 全量新值与原版本
     * @return 修改后的命名空间；与当前完全相同时不递增版本
     */
    @PutExchange("/applications/{applicationCode}/namespaces/{namespaceCode}")
    NamespaceResponseBody updateNamespace(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("applicationCode") String applicationCode,
        @PathVariable("namespaceCode") String namespaceCode, @Valid @RequestBody UpdateNamespaceRequestBody request);

    /**
     * 删除命名空间（物理删除）
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param applicationCode 应用编码
     * @param namespaceCode 命名空间编码
     * @param expectedVersion 调用方读取的原版本
     *         <li>成功返回 200 无正文；其下还有任何 key（含已废弃）时返回 400。</li>
     */
    @DeleteExchange("/applications/{applicationCode}/namespaces/{namespaceCode}")
    void deleteNamespace(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("applicationCode") String applicationCode,
        @PathVariable("namespaceCode") String namespaceCode, @RequestParam("expectedVersion") Integer expectedVersion);
}
