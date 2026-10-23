package com.paytech.platform.support.localization.service.processor;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nLocale;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nTranslation;
import com.paytech.platform.support.localization.core.enums.LocaleStatus;
import com.paytech.platform.support.localization.domain.LocalizationCodes;
import com.paytech.platform.support.localization.domain.TranslationWrite;

import lombok.AllArgsConstructor;

/** 保存与导入共用的翻译写入准备：目标语言检查与写入行构造。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class TranslationWrites {

    private final LocalizationProcessorSupport support;

    /**
     * 目标语言须存在、ENABLED 且不是 en（en 由英文基准提供，不存翻译）。
     *
     * @param localeCode 语言代码
     */
    void requireTranslatable(String localeCode) {
        if (LocalizationCodes.BASE_LOCALE.equals(localeCode)) {
            throw new BizCheckFailException("en 是基准语言，不维护翻译");
        }
        I18nLocale locale = support.requireLocale(localeCode);
        if (locale.getStatus() != LocaleStatus.ENABLED) {
            throw new BizCheckFailException("语言未启用: " + localeCode);
        }
    }

    /**
     * 一条写入：新建时生成完整新行，更新时只带目标值；翻译依据当前基准修订号。
     *
     * @param key 所属 key
     * @param existing 已有翻译；新建时为 null
     * @param locale 语言代码
     * @param text 新文案
     * @param actorId 操作人
     * @return 写入
     */
    TranslationWrite write(I18nKey key, I18nTranslation existing, String locale, String text, long actorId) {
        Instant now = support.now();
        I18nTranslation next = new I18nTranslation();
        next.setText(text);
        next.setBasedOnSourceRevision(key.getSourceRevision());
        next.setUpdatedBy(actorId);
        next.setUpdateTime(now);
        if (existing == null) {
            next.setTranslationId(support.nextId());
            next.setKeyId(key.getKeyId());
            next.setLocaleCode(locale);
            next.setVersion(1);
            next.setCreatedBy(actorId);
            next.setCreateTime(now);
        }
        return new TranslationWrite(key, existing, next);
    }
}
