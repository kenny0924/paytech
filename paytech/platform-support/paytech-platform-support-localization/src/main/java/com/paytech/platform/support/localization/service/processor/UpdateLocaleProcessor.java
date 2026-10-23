package com.paytech.platform.support.localization.service.processor;

import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.api.localization.response.LocaleResponseBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nLocale;
import com.paytech.platform.support.localization.core.enums.LocaleStatus;
import com.paytech.platform.support.localization.domain.LocalizationCodes;
import com.paytech.platform.support.localization.domainservice.service.LocalizationCatalogService;
import com.paytech.platform.support.localization.service.processor.request.LocaleCommand;

import lombok.AllArgsConstructor;

/**
 * 修改语言名称、文字方向、状态与排序，版本加一
 * <li>按语言代码持资源锁；en 不能停用。停用时在写事务内重证没有应用把它用作默认或必须覆盖语言（应用写入对语言加共享锁）。</li>
 * <li>与当前完全相同时不写入、不递增版本。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateLocaleProcessor extends AbstractLockProcessor<LocaleCommand, LocaleResponseBody> {

    private final LocalizationCatalogService catalog;
    private final LocalizationProcessorSupport support;

    @Override
    protected void validate(LocaleCommand request) {
        if (request.getExpectedVersion() == null || request.getStatus() == null) {
            throw new InvalidParameterException("修改语言须提供 expectedVersion 与 status");
        }
        if (LocalizationCodes.BASE_LOCALE.equals(request.getLocaleCode())
            && request.getStatus() == LocaleStatus.DISABLED) {
            throw new BizCheckFailException("基准语言 en 不能停用");
        }
    }

    @Override
    protected LockCommand lockCommand(LocaleCommand request) {
        return LockCommand.tryLock(LocalizationProcessorSupport.localeLock(request.getLocaleCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected LocaleResponseBody processWithinLock(LocaleCommand request) {
        SessionContext session = support.authorize(request.getActor(), request.getSession(),
            LocalizationProcessorSupport.EDIT_PERMISSION);
        I18nLocale locale = support.requireLocale(request.getLocaleCode());
        LocalizationProcessorSupport.requireVersion(locale.getVersion(), request.getExpectedVersion(),
            "语言 " + locale.getLocaleCode());
        I18nLocale next = next(request, support.actorId(session));
        if (same(locale, next)) {
            return LocalizationResponses.locale(locale);
        }
        return support.write(request.getActor(), request.getSession(), LocalizationProcessorSupport.EDIT_PERMISSION,
            () -> {
                catalog.updateLocale(locale, next);
                support.recordSucceeded(session, LocalizationProcessorSupport.LOCALE_AUDIT_TYPE, locale.getLocaleId(),
                    "修改语言 " + locale.getLocaleCode() + "，状态 " + locale.getStatus().getCode());
                return LocalizationResponses.locale(locale);
            });
    }

    /** 目标值。 */
    I18nLocale next(LocaleCommand request, long actorId) {
        I18nLocale next = new I18nLocale();
        next.setNativeName(request.getNativeName());
        next.setEnglishName(request.getEnglishName());
        next.setTextDirection(request.getTextDirection());
        next.setStatus(request.getStatus());
        next.setSortOrder(request.getSortOrder());
        next.setUpdatedBy(actorId);
        next.setUpdateTime(support.now());
        return next;
    }

    /** 可修改字段是否全部相同。 */
    static boolean same(I18nLocale locale, I18nLocale next) {
        return Objects.equals(locale.getNativeName(), next.getNativeName())
            && Objects.equals(locale.getEnglishName(), next.getEnglishName())
            && locale.getTextDirection() == next.getTextDirection() && locale.getStatus() == next.getStatus()
            && Objects.equals(locale.getSortOrder(), next.getSortOrder());
    }
}
