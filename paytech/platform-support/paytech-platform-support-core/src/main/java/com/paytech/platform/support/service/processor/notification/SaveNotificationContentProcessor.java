package com.paytech.platform.support.service.processor.notification;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.delivery.domain.NotificationConfigurationChange;
import com.paytech.platform.support.delivery.domain.SaveNotificationContentCommand;
import com.paytech.platform.support.delivery.service.NotificationManagementService;
import com.paytech.platform.support.service.processor.notification.request.SaveNotificationContentRequest;

import lombok.AllArgsConstructor;

/**
 * 新建或修改通知模板某一语言的标题与正文
 * <li>按发送时的同一规则校验，问题定位到 subjectTemplate / bodyTemplate 并返回 422；认证邮件模板正文必须包含 ${link}</li>
 * <li>鉴权、校验、写入与配置审计同一事务；审计只记录标题与正文的 SHA-256 摘要</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class SaveNotificationContentProcessor
    extends AbstractLockProcessor<SaveNotificationContentRequest, NotificationConfigurationChange> {

    private final NotificationManagementSupport support;
    private final NotificationManagementService management;

    /** 按模板编码协调，与同一模板的启停、路由修改互斥。 */
    @Override
    protected LockCommand lockCommand(SaveNotificationContentRequest request) {
        return LockCommand.tryLock(NotificationManagementSupport.templateLockKey(request.getTemplateCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected NotificationConfigurationChange processWithinLock(SaveNotificationContentRequest request) {
        return support.write(request.getActor(), request.getSession(), "NOTIFICATION_CONTENT",
            request.getTemplateCode() + ":" + request.getLocale(), request.getReason(), now -> save(request, now));
    }

    /** 授权之后校验内容，校验失败不写入。 */
    NotificationConfigurationChange save(SaveNotificationContentRequest request, Instant now) {
        var issues = management.contentIssues(request.getTemplateCode(), request.getSubjectTemplate(),
            request.getBodyTemplate());
        if (!issues.isEmpty()) {
            throw new RequestBodyValidationException(
                issues.stream().map(issue -> new ProblemFieldError(issue.getField(), issue.getMessage())).toList(),
                null);
        }
        return management.saveContent(new SaveNotificationContentCommand(request.getTemplateCode(),
            request.getLocale(), request.getSubjectTemplate(), request.getBodyTemplate(), request.getExpectedVersion(),
            now));
    }
}
