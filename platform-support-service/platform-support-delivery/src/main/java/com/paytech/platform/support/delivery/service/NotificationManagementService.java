package com.paytech.platform.support.delivery.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;
import com.paytech.platform.support.delivery.domain.ChangeNotificationStatusCommand;
import com.paytech.platform.support.delivery.domain.NotificationConfigurationChange;
import com.paytech.platform.support.delivery.domain.NotificationPreview;
import com.paytech.platform.support.delivery.domain.NotificationProviderView;
import com.paytech.platform.support.delivery.domain.NotificationTemplateDetailView;
import com.paytech.platform.support.delivery.domain.NotificationTemplateIssue;
import com.paytech.platform.support.delivery.domain.NotificationTemplateSummary;
import com.paytech.platform.support.delivery.domain.SaveNotificationContentCommand;
import com.paytech.platform.support.delivery.domain.SaveNotificationRouteCommand;

/**
 * 运营维护通知模板、语言内容、路由与发送账号
 * <li>只读写本库；写方法加入调用方事务，由调用方负责鉴权、资源锁与审计</li>
 * <li>修改按 expectedVersion 与条件更新保护，版本不符返回并发冲突</li>
 */
public interface NotificationManagementService {

    /**
     * 按编码升序分页查询模板
     *
     * @param keyword 已转义的编码 LIKE 模式，可空
     * @param status 状态筛选，可空
     */
    PagingResponseBody<NotificationTemplateSummary> templates(String keyword, NotificationConfigurationStatus status,
        PagingRequestBody paging);

    /** 模板详情；不存在返回 NOT_FOUND。 */
    NotificationTemplateDetailView template(String templateCode);

    /** 新建停用状态的 EMAIL 模板；编码已存在时拒绝。 */
    NotificationConfigurationChange createTemplate(String templateCode, Instant now);

    /** 启停模板；启用时至少要有一条语言内容。 */
    NotificationConfigurationChange changeTemplateStatus(ChangeNotificationStatusCommand command);

    /**
     * 按发送规则检查语言内容；即时发送模板的正文必须包含链接参数
     *
     * @return 字段问题，为空表示可保存；模板不存在返回 NOT_FOUND
     */
    List<NotificationTemplateIssue> contentIssues(String templateCode, String subject, String body);

    /** 新建或修改某一语言的内容；调用前应已通过 contentIssues。 */
    NotificationConfigurationChange saveContent(SaveNotificationContentCommand command);

    /** 渲染草稿，缺少的参数保留原占位符；不读库、不发送。 */
    NotificationPreview preview(String subject, String body, Map<String, String> parameters);

    /** 新建或修改路由；主备账号必须是启用的 EMAIL 账号且互不相同。 */
    NotificationConfigurationChange saveRoute(SaveNotificationRouteCommand command);

    /** 启停路由；启用时重新检查主备账号。 */
    NotificationConfigurationChange changeRouteStatus(ChangeNotificationStatusCommand command);

    /** 按编码升序分页查询发送账号及其路由引用。 */
    PagingResponseBody<NotificationProviderView> providers(PagingRequestBody paging);

    /** 启停发送账号；不检查引用，由调用方提示影响。 */
    NotificationConfigurationChange changeProviderStatus(ChangeNotificationStatusCommand command);
}
