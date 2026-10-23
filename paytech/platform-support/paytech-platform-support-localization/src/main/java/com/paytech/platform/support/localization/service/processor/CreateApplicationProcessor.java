package com.paytech.platform.support.localization.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.api.localization.response.ApplicationResponseBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.enums.ApplicationStatus;
import com.paytech.platform.support.localization.domain.LocalizationJson;
import com.paytech.platform.support.localization.domainservice.service.LocalizationCatalogService;
import com.paytech.platform.support.localization.service.processor.request.ApplicationCommand;

import lombok.AllArgsConstructor;

/**
 * 新增应用：ENABLED、版本 1、修订号 0、尚无发布
 * <li>按应用编码持资源锁；默认语言与必须覆盖语言须为 ENABLED（写事务内加共享锁重证），编码唯一键为最终保护。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateApplicationProcessor extends AbstractLockProcessor<ApplicationCommand, ApplicationResponseBody> {

    private final LocalizationCatalogService catalog;
    private final LocalizationProcessorSupport support;

    @Override
    protected void validate(ApplicationCommand request) {
        CatalogRules.requireDistinctLocales(request);
    }

    @Override
    protected LockCommand lockCommand(ApplicationCommand request) {
        return LockCommand.tryLock(LocalizationProcessorSupport.applicationLock(request.getApplicationCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected ApplicationResponseBody processWithinLock(ApplicationCommand request) {
        SessionContext session = support.authorize(request.getActor(), request.getSession(),
            LocalizationProcessorSupport.EDIT_PERMISSION);
        I18nApplication application = newApplication(request, support.actorId(session));
        return support.write(request.getActor(), request.getSession(), LocalizationProcessorSupport.EDIT_PERMISSION,
            () -> {
                catalog.createApplication(application, CatalogRules.referencedLocales(request));
                support.recordSucceeded(session, LocalizationProcessorSupport.APPLICATION_AUDIT_TYPE,
                    application.getApplicationId(), "新增应用 " + application.getApplicationCode());
                return LocalizationResponses.application(application, null, 0);
            });
    }

    /** 新应用。 */
    I18nApplication newApplication(ApplicationCommand request, long actorId) {
        I18nApplication application = new I18nApplication();
        application.setApplicationId(support.nextId());
        application.setApplicationCode(request.getApplicationCode());
        application.setApplicationName(request.getApplicationName());
        application.setDefaultLocale(request.getDefaultLocale());
        application.setRequiredLocalesJson(LocalizationJson.writeLocales(request.getRequiredLocales()));
        application.setCoverageThresholdPercent(request.getCoverageThresholdPercent());
        application.setKeySetRevision(0);
        application.setContentRevision(0);
        application.setStatus(ApplicationStatus.ENABLED);
        application.setVersion(1);
        application.setCreatedBy(actorId);
        application.setUpdatedBy(actorId);
        application.setCreateTime(support.now());
        application.setUpdateTime(application.getCreateTime());
        return application;
    }
}
