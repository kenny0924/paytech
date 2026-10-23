package com.paytech.platform.support.localization.service.processor;

import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.api.localization.response.ApplicationResponseBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.domain.LocalizationJson;
import com.paytech.platform.support.localization.domainservice.service.LocalizationCatalogService;
import com.paytech.platform.support.localization.service.processor.request.ApplicationCommand;

import lombok.AllArgsConstructor;

/**
 * 修改应用名称、默认语言、必须覆盖语言与阈值，版本加一
 * <li>按应用编码持资源锁；引用的语言须为 ENABLED（写事务内加共享锁重证）。与当前完全相同时不写入。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateApplicationProcessor extends AbstractLockProcessor<ApplicationCommand, ApplicationResponseBody> {

    private final LocalizationQueryLookups lookups;
    private final LocalizationCatalogService catalog;
    private final LocalizationProcessorSupport support;

    @Override
    protected void validate(ApplicationCommand request) {
        if (request.getExpectedVersion() == null) {
            throw new InvalidParameterException("修改应用须提供 expectedVersion");
        }
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
        I18nApplication application = support.requireApplication(request.getApplicationCode());
        LocalizationProcessorSupport.requireVersion(application.getVersion(), request.getExpectedVersion(),
            "应用 " + application.getApplicationCode());
        I18nApplication next = next(request, support.actorId(session));
        if (!same(application, next)) {
            support.write(request.getActor(), request.getSession(), LocalizationProcessorSupport.EDIT_PERMISSION, () -> {
                catalog.updateApplication(application, next, CatalogRules.referencedLocales(request));
                support.recordSucceeded(session, LocalizationProcessorSupport.APPLICATION_AUDIT_TYPE,
                    application.getApplicationId(), "修改应用设置 " + application.getApplicationCode());
                return null;
            });
        }
        return LocalizationResponses.application(application, lookups.currentReleaseNo(application),
            lookups.pendingChangeCount(application));
    }

    /** 目标设置。 */
    I18nApplication next(ApplicationCommand request, long actorId) {
        I18nApplication next = new I18nApplication();
        next.setApplicationName(request.getApplicationName());
        next.setDefaultLocale(request.getDefaultLocale());
        next.setRequiredLocalesJson(LocalizationJson.writeLocales(request.getRequiredLocales()));
        next.setCoverageThresholdPercent(request.getCoverageThresholdPercent());
        next.setUpdatedBy(actorId);
        next.setUpdateTime(support.now());
        return next;
    }

    /** 设置字段是否全部相同；必须覆盖语言按顺序比较。 */
    static boolean same(I18nApplication application, I18nApplication next) {
        return Objects.equals(application.getApplicationName(), next.getApplicationName())
            && Objects.equals(application.getDefaultLocale(), next.getDefaultLocale())
            && LocalizationJson.readLocales(application.getRequiredLocalesJson())
                .equals(LocalizationJson.readLocales(next.getRequiredLocalesJson()))
            && Objects.equals(application.getCoverageThresholdPercent(), next.getCoverageThresholdPercent());
    }
}
