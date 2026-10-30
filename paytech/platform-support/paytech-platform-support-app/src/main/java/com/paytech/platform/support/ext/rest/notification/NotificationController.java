package com.paytech.platform.support.ext.rest.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.api.notification.NotificationCommandApi;
import com.paytech.platform.support.api.notification.request.SubmitNotificationRequestBody;
import com.paytech.platform.support.api.notification.response.SubmitNotificationResponseBody;
import com.paytech.platform.support.configuration.NotificationCallerProperties;
import com.paytech.platform.support.delivery.service.processor.SubmitNotificationProcessor;
import com.paytech.platform.support.ext.rest.internalauth.InternalCallerResolver;
import com.paytech.platform.support.service.processor.notification.SubmitTenantNotificationProcessor;

import lombok.AllArgsConstructor;

/**
 * 普通通知内部提交的协议适配；不开事务。
 * <li>先按白名单校验调用方与模板，未通过时不调用 Vault、不写库</li>
 * <li>调用方服务名作为来源系统交给 delivery 模块的 Processor；携带已验证内部 Token 时以 Token sub 为准（D26）</li>
 * <li>按租户权限解析收件人的请求交给组合 Processor：先在本库解析收件人，再委托 delivery 受理</li>
 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "notification.enabled", havingValue = "true")
public class NotificationController implements NotificationCommandApi {

    private final NotificationCallerProperties callers;
    private final NotificationConverter converter;
    private final InternalCallerResolver internalCallers;
    private final SubmitNotificationProcessor processor;
    private final SubmitTenantNotificationProcessor tenantProcessor;

    /** 授权后按收件人来源转换并委托受理；两种来源二选一已由 Body 校验保证。 */
    @Override
    public SubmitNotificationResponseBody submitNotification(String headerCaller,
        SubmitNotificationRequestBody requestBody) {
        String callerService = internalCallers.resolve(headerCaller);
        callers.authorize(callerService, requestBody.getTemplateCode());
        if (requestBody.getTenantRecipients() != null) {
            var response = tenantProcessor.process(converter.convertTenantRequest(callerService, requestBody));
            return converter.convertTenantResponse(response);
        }
        var response = processor.process(converter.convertRequest(callerService, requestBody));
        return converter.convertResponse(response);
    }
}
