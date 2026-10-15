package com.paytech.platform.support.ext.rest.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.api.notification.NotificationCommandApi;
import com.paytech.platform.support.api.notification.request.SubmitNotificationRequestBody;
import com.paytech.platform.support.api.notification.response.SubmitNotificationResponseBody;
import com.paytech.platform.support.configuration.NotificationCallerProperties;
import com.paytech.platform.support.delivery.service.processor.SubmitNotificationProcessor;

import lombok.AllArgsConstructor;

/**
 * 普通通知内部提交的协议适配；不开事务。
 * <li>先按白名单校验调用方与模板，未通过时不调用 Vault、不写库</li>
 * <li>调用方服务名作为来源系统交给 delivery 模块的 Processor</li>
 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "notification.enabled", havingValue = "true")
public class NotificationController implements NotificationCommandApi {

    private final NotificationCallerProperties callers;
    private final NotificationConverter converter;
    private final SubmitNotificationProcessor processor;

    /** 授权后转换并委托受理。 */
    @Override
    public SubmitNotificationResponseBody submitNotification(String callerService,
        SubmitNotificationRequestBody requestBody) {
        callers.authorize(callerService, requestBody.getTemplateCode());
        var response = processor.process(converter.convertRequest(callerService, requestBody));
        return converter.convertResponse(response);
    }
}
