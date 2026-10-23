package com.paytech.platform.support.localization.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.api.localization.response.LocaleResponseBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nLocale;
import com.paytech.platform.support.localization.core.enums.LocaleStatus;
import com.paytech.platform.support.localization.domainservice.service.LocalizationCatalogService;
import com.paytech.platform.support.localization.service.processor.request.LocaleCommand;

import lombok.AllArgsConstructor;

/**
 * 新增语言，状态 ENABLED，版本 1
 * <li>按语言代码持资源锁；复核 I18N_EDIT 后在一个本地事务内插入，语言代码唯一键为最终保护（已存在返回 400）。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateLocaleProcessor extends AbstractLockProcessor<LocaleCommand, LocaleResponseBody> {

    private final LocalizationCatalogService catalog;
    private final LocalizationProcessorSupport support;

    @Override
    protected LockCommand lockCommand(LocaleCommand request) {
        return LockCommand.tryLock(LocalizationProcessorSupport.localeLock(request.getLocaleCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected LocaleResponseBody processWithinLock(LocaleCommand request) {
        SessionContext session = support.authorize(request.getActor(), request.getSession(),
            LocalizationProcessorSupport.EDIT_PERMISSION);
        I18nLocale locale = newLocale(request, support.actorId(session));
        return support.write(request.getActor(), request.getSession(), LocalizationProcessorSupport.EDIT_PERMISSION,
            () -> {
                catalog.createLocale(locale);
                support.recordSucceeded(session, LocalizationProcessorSupport.LOCALE_AUDIT_TYPE, locale.getLocaleId(),
                    "新增语言 " + locale.getLocaleCode());
                return LocalizationResponses.locale(locale);
            });
    }

    /** 新语言：ENABLED、版本 1。 */
    I18nLocale newLocale(LocaleCommand request, long actorId) {
        I18nLocale locale = new I18nLocale();
        locale.setLocaleId(support.nextId());
        locale.setLocaleCode(request.getLocaleCode());
        locale.setNativeName(request.getNativeName());
        locale.setEnglishName(request.getEnglishName());
        locale.setTextDirection(request.getTextDirection());
        locale.setStatus(LocaleStatus.ENABLED);
        locale.setSortOrder(request.getSortOrder());
        locale.setVersion(1);
        locale.setCreatedBy(actorId);
        locale.setUpdatedBy(actorId);
        locale.setCreateTime(support.now());
        locale.setUpdateTime(locale.getCreateTime());
        return locale;
    }
}
