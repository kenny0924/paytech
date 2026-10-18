package com.paytech.platform.support.api.notificationmanagement;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.*;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.notificationmanagement.request.*;
import com.paytech.platform.support.api.notificationmanagement.response.*;

import jakarta.validation.Valid;

/**
 * Operations 维护通知模板、语言内容、路由与发送账号，查询普通通知发送记录
 * <li>每次调用重检普通 Operations 会话；读需要 NOTIFICATION_READ，写需要 NOTIFICATION_MANAGE，
 * 发送账号密钥与测试发送需要 NOTIFICATION_CREDENTIAL_MANAGE</li>
 * <li>写请求带 expectedVersion 与 reason，版本不符返回 409，每次写入记 Platform 审计；请求幂等归 Gateway</li>
 * <li>修改立即作用于之后的发送；不提供删除</li>
 */
@HttpExchange("/notification-admin/v1")
public interface NotificationManagementApi {

    /**
     * 按编码升序分页查询模板
     *
     * @param actor
     *            可信 Operations 账号标识
     * @param session
     *            当前普通 Operations 会话
     * @param page
     *            页码，默认 1
     * @param size
     *            每页条数，默认 10，仅允许 10、20、50、100
     * @param keyword
     *            可选编码包含查询，最多 64 字符，通配符按文字处理
     * @param status
     *            可选状态 ACTIVE、DISABLED
     * @return 模板及其语言、路由状态
     */
    @GetExchange("/templates")
    PagingResponseBody<NotificationTemplateSummaryResponseBody> templates(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "status", required = false) String status);

    /**
     * 读取模板、全部语言内容与路由
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param templateCode 精确模板编码，不存在返回 404
     * @return 模板详情
     */
    @GetExchange("/templates/{templateCode}")
    NotificationTemplateDetailResponseBody template(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("templateCode") String templateCode);

    /**
     * 新建停用状态的 EMAIL 模板
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param request 编码与原因；编码已存在返回 400
     * @return 模板编码与版本 1
     */
    @PostExchange("/templates")
    NotificationMutationResponseBody createTemplate(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @Valid @RequestBody CreateNotificationTemplateRequestBody request);

    /**
     * 启用或停用模板；启用前至少要有一条语言内容
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param templateCode 精确模板编码，不存在返回 404
     * @param request 目标状态、当前版本与原因
     * @return 模板编码与新版本
     */
    @PatchExchange("/templates/{templateCode}/status")
    NotificationMutationResponseBody changeTemplateStatus(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("templateCode") String templateCode,
        @Valid @RequestBody ChangeNotificationStatusRequestBody request);

    /**
     * 新建或修改某一语言的标题与正文
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param templateCode 精确模板编码，不存在返回 404
     * @param locale BCP 47 语言，按规范形式保存
     * @param request 标题、正文、当前版本（0 为新建）与原因；内容不符合发送规则返回 422 并定位字段
     * @return 语言与新版本
     */
    @PutExchange("/templates/{templateCode}/contents/{locale}")
    NotificationMutationResponseBody saveContent(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("templateCode") String templateCode,
        @PathVariable("locale") String locale, @Valid @RequestBody SaveNotificationContentRequestBody request);

    /**
     * 渲染内容草稿；不读写配置、不调用 Vault 或供应商、不记成功审计
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话，要求 NOTIFICATION_MANAGE
     * @param request 标题、正文与示例参数
     * @return 渲染结果与缺少的参数
     */
    @PostExchange("/template-preview")
    NotificationPreviewResponseBody preview(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @Valid @RequestBody PreviewNotificationContentRequestBody request);

    /**
     * 新建或修改模板路由的主备账号
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param templateCode 精确模板编码，不存在返回 404
     * @param request 主备账号、当前版本（0 为新建）与原因
     * @return 模板编码与路由新版本
     */
    @PutExchange("/templates/{templateCode}/route")
    NotificationMutationResponseBody saveRoute(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("templateCode") String templateCode,
        @Valid @RequestBody SaveNotificationRouteRequestBody request);

    /**
     * 启用或停用模板路由；启用时主备账号必须仍可用
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param templateCode 精确模板编码；模板或路由不存在返回 404
     * @param request 目标状态、路由当前版本与原因
     * @return 模板编码与路由新版本
     */
    @PatchExchange("/templates/{templateCode}/route/status")
    NotificationMutationResponseBody changeRouteStatus(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("templateCode") String templateCode,
        @Valid @RequestBody ChangeNotificationStatusRequestBody request);

    /**
     * 按编码升序分页查询发送账号及其路由引用
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，仅允许 10、20、50、100
     * @return 账号列表，含区域、发件地址与配置完整情况，不含密钥
     */
    @GetExchange("/providers")
    PagingResponseBody<NotificationProviderResponseBody> providers(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 新建停用状态的发送账号
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param request 编码、类型、区域、发件地址、Configuration Set 与原因；编码已存在返回 400
     * @return 账号编码与版本 1
     */
    @PostExchange("/providers")
    NotificationMutationResponseBody createProvider(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @Valid @RequestBody CreateNotificationProviderRequestBody request);

    /**
     * 修改发送账号的区域、发件地址与 Configuration Set；立即作用于之后的发送
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param providerCode 精确账号编码，不存在返回 404
     * @param request 新配置、当前版本与原因
     * @return 账号编码与新版本
     */
    @PutExchange("/providers/{providerCode}")
    NotificationMutationResponseBody updateProvider(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("providerCode") String providerCode,
        @Valid @RequestBody UpdateNotificationProviderRequestBody request);

    /**
     * 录入或更换发送账号的密钥；密钥存入 Vault，账号只保存其 Token
     * <li>Vault 不可用或未授权返回 503，账号不变；任何响应、日志与审计都不含密钥与 Token</li>
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param providerCode 精确账号编码，不存在返回 404
     * @param request 密钥对、当前版本与原因
     * @return 账号编码与新版本
     */
    @PutExchange("/providers/{providerCode}/credential")
    NotificationMutationResponseBody replaceProviderCredential(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("providerCode") String providerCode,
        @Valid @RequestBody ReplaceNotificationCredentialRequestBody request);

    /**
     * 用指定账号向一个邮箱发送固定内容的测试邮件；不生成发送记录，只记审计
     * <li>账号无需启用，但配置须完整；本环境未开启发送或未启用该服务商返回 503</li>
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param providerCode 精确账号编码，不存在返回 404
     * @param request 收件邮箱与原因
     * @return 服务商结果分类与结果码
     */
    @PostExchange("/providers/{providerCode}/test-send")
    NotificationProviderTestSendResponseBody testSendProvider(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("providerCode") String providerCode,
        @Valid @RequestBody TestSendNotificationProviderRequestBody request);

    /**
     * 启用或停用发送账号；启用要求配置完整，不检查引用，停用后引用它的路由将无法发送
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param providerCode 精确账号编码，不存在返回 404
     * @param request 目标状态、当前版本与原因
     * @return 账号编码与新版本
     */
    @PatchExchange("/providers/{providerCode}/status")
    NotificationMutationResponseBody changeProviderStatus(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("providerCode") String providerCode,
        @Valid @RequestBody ChangeNotificationStatusRequestBody request);

    /**
     * 按创建时间倒序分页查询普通通知发送记录；认证邮件不留记录
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，仅允许 10、20、50、100
     * @param createdFrom 创建时间下限（含），ISO-8601 UTC，必填
     * @param createdTo 创建时间上限（不含），ISO-8601 UTC，必填，跨度不超过 31 天
     * @param sourceSystem 可选来源系统精确值
     * @param sourceEventKey 可选来源事件键精确值
     * @param templateCode 可选模板编码精确值
     * @param deliveryStatus 可选投递状态，匹配至少一个投递处于该状态的请求
     * @return 请求列表，不含收件邮箱与参数值
     */
    @GetExchange("/requests")
    PagingResponseBody<NotificationRequestSummaryResponseBody> requests(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "createdFrom", required = false) String createdFrom,
        @RequestParam(value = "createdTo", required = false) String createdTo,
        @RequestParam(value = "sourceSystem", required = false) String sourceSystem,
        @RequestParam(value = "sourceEventKey", required = false) String sourceEventKey,
        @RequestParam(value = "templateCode", required = false) String templateCode,
        @RequestParam(value = "deliveryStatus", required = false) String deliveryStatus);

    /**
     * 读取发送记录详情
     *
     * @param actor 可信 Operations 账号标识
     * @param session 当前普通 Operations 会话
     * @param requestId 请求 ID，十进制字符串，不存在返回 404
     * @return 请求、投递与尝试，不含收件邮箱、参数值和供应商流水号
     */
    @GetExchange("/requests/{requestId}")
    NotificationRequestDetailResponseBody request(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("requestId") String requestId);
}
