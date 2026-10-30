package com.paytech.platform.support.ext.rest.notification;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.accesscontrol.domain.enums.AccessTenantType;
import com.paytech.platform.support.api.notification.request.SubmitNotificationRequestBody;
import com.paytech.platform.support.api.notification.response.SubmitNotificationResponseBody;
import com.paytech.platform.support.delivery.service.processor.request.SubmitNotificationRequest;
import com.paytech.platform.support.delivery.service.processor.response.SubmitNotificationResponse;
import com.paytech.platform.support.service.processor.notification.request.SubmitTenantNotificationRequest;
import com.paytech.platform.support.service.processor.notification.response.SubmitTenantNotificationResponse;

/** 通知提交的协议转换：调用方服务名作为来源系统，字符串 ID 与内部 long 互转。 */
@Component
public class NotificationConverter {

    /** 显式收件人的 Body 转模块请求；sourceSystem 只取已授权的调用方服务名。 */
    public SubmitNotificationRequest convertRequest(String callerService, SubmitNotificationRequestBody body) {
        var request = notification(callerService, body);
        request.setRecipients(body.getRecipients().stream().map(NotificationConverter::recipient).toList());
        return request;
    }

    /** 按租户权限解析收件人的 Body 转组合请求；收件人由 Processor 解析后填入。 */
    public SubmitTenantNotificationRequest convertTenantRequest(String callerService, SubmitNotificationRequestBody body) {
        var criteria = body.getTenantRecipients();
        var request = new SubmitTenantNotificationRequest();
        request.setNotification(notification(callerService, body));
        request.setTenantType(tenantType(criteria.getTenantType()));
        request.setOwnerRefId(positiveLong(criteria.getOwnerRefId(), "ownerRefId 超出范围"));
        request.setPermissionCode(criteria.getPermissionCode());
        request.setLocale(criteria.getLocale());
        return request;
    }

    /** 模块响应转 Body；投递数与受理人数相同。 */
    public SubmitNotificationResponseBody convertResponse(SubmitNotificationResponse response) {
        return new SubmitNotificationResponseBody(Long.toString(response.getRequestId()), response.getAcceptedCount(),
            response.getAcceptedCount());
    }

    /** 组合响应转 Body；0 人时 requestId 为空。 */
    public SubmitNotificationResponseBody convertTenantResponse(SubmitTenantNotificationResponse response) {
        String requestId = response.getRequestId() == null ? null : Long.toString(response.getRequestId());
        return new SubmitNotificationResponseBody(requestId, response.getDeliveryCount(), response.getDeliveryCount());
    }

    /** 两种收件人来源共用的来源、租户、模板、语言与参数。 */
    static SubmitNotificationRequest notification(String callerService, SubmitNotificationRequestBody body) {
        var request = new SubmitNotificationRequest();
        request.setSourceSystem(callerService);
        request.setTenantId(tenantId(body.getTenantId()));
        request.setSourceEventKey(body.getSourceEventKey());
        request.setTemplateCode(body.getTemplateCode());
        request.setDefaultLocale(body.getDefaultLocale());
        request.setDefaultParameters(body.getDefaultParameters());
        return request;
    }

    /** 已通过格式校验的租户类型 Code。 */
    static AccessTenantType tenantType(String code) {
        for (AccessTenantType type : AccessTenantType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new InvalidParameterException("tenantType 不受支持");
    }

    /** 已通过格式校验的租户 ID；超出 long 范围按参数错误处理。 */
    static Long tenantId(String value) {
        if (value == null) {
            return null;
        }
        return positiveLong(value, "tenantId 超出范围");
    }

    /** 已通过正整数格式校验的字符串 ID；超出 long 范围按参数错误处理。 */
    static long positiveLong(String value, String overflowMessage) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException overflow) {
            throw new InvalidParameterException(overflowMessage);
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
