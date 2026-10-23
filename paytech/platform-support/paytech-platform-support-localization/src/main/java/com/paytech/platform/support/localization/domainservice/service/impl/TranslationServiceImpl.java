package com.paytech.platform.support.localization.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.domain.TranslationWrite;
import com.paytech.platform.support.localization.domain.TranslationWriteContext;
import com.paytech.platform.support.localization.domainservice.repository.I18nApplicationRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nTranslationRepository;
import com.paytech.platform.support.localization.domainservice.service.TranslationService;

import lombok.AllArgsConstructor;

/** 翻译写入：应用行锁与导入、发布登记串行；已有翻译按事务前读取的版本 CAS，新建由唯一键兜底。 */
@Service
@AllArgsConstructor
public class TranslationServiceImpl implements TranslationService {

    private final I18nTranslationRepository translations;
    private final I18nApplicationRepository applications;

    /** 逐条写入后推进文案修订号。 */
    @Override
    public int saveTranslations(TranslationWriteContext context) {
        I18nApplication application = applications.lockById(context.getApplication().getApplicationId());
        if (application == null) {
            throw new ConcurrentRequestException("应用已不存在: " + context.getApplication().getApplicationCode());
        }
        if (context.getWrites().isEmpty()) {
            return application.getContentRevision();
        }
        for (TranslationWrite write : context.getWrites()) {
            if (write.getExisting() == null) {
                translations.insert(write.getNext());
            } else {
                translations.update(write.getExisting(), write.getNext());
            }
        }
        applications.advanceContent(application, context.getNow());
        return application.getContentRevision();
    }
}
