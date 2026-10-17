package com.paytech.platform.support.ext.rest.notification;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.api.notification.request.SubmitNotificationRequestBody;
import com.paytech.platform.support.api.notification.response.SubmitNotificationResponseBody;
import com.paytech.platform.support.delivery.service.processor.request.SubmitNotificationRequest;
import com.paytech.platform.support.delivery.service.processor.response.SubmitNotificationResponse;

/** 通知提交的协议转换：调用方服务名作为来源系统，字符串 ID 与内部 long 互转。 */
@Component
public class NotificationConverter {

    /** 已通过 @Valid 的 Body 转模块请求；sourceSystem 只取已授权的调用方服务名。 */
    public SubmitNotificationRequest convertRequest(String callerService, SubmitNotificationRequestBody body) {
        var request = new SubmitNotificationRequest();
        request.setSourceSystem(callerService);
        request.setTenantId(tenantId(body.getTenantId()));
        request.setSourceEventKey(body.getSourceEventKey());
        request.setTemplateCode(body.getTemplateCode());
        request.setDefaultLocale(body.getDefaultLocale());
        request.setDefaultParameters(body.getDefaultParameters());
        request.setRecipients(body.getRecipients().stream().map(NotificationConverter::recipient).toList());
        return request;
    }

    /** 模块响应转 Body。 */
    public SubmitNotificationResponseBody convertResponse(SubmitNotificationResponse response) {
        return new SubmitNotificationResponseBody(Long.toString(response.getRequestId()), response.getAcceptedCount());
    }

    /** 已通过格式校验的租户 ID；超出 long 范围按参数错误处理。 */
    static Long tenantId(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException overflow) {
            throw new InvalidParameterException("tenantId 超出范围");
        }
    }

    /** 收件人逐项转换；列表与元素非空已由 Body 校验保证。 */
    static SubmitNotificationRequest.Recipient recipient(SubmitNotificationRequestBody.Recipient body) {
        var recipient = new SubmitNotificationRequest.Recipient();
        recipient.setEmail(body.getEmail());
        recipient.setLocale(body.getLocale());
        recipient.setParameters(body.getParameters());
        return recipient;
    }
}
