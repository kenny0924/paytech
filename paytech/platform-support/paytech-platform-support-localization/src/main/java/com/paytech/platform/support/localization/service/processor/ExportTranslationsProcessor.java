package com.paytech.platform.support.localization.service.processor;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.localization.response.TranslationFileResponseBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nTranslation;
import com.paytech.platform.support.localization.core.enums.KeyStatus;
import com.paytech.platform.support.localization.domain.LocalizationCodes;
import com.paytech.platform.support.localization.domainservice.repository.I18nKeyRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nTranslationRepository;
import com.paytech.platform.support.localization.service.processor.request.TranslationExportQuery;

import lombok.AllArgsConstructor;

/**
 * 导出一个语言的翻译：复核 I18N_VIEW 后读取全部 ACTIVE key，未翻译为空字符串，命名空间与 key 均按字典序；不开事务。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ExportTranslationsProcessor extends AbstractProcessor<TranslationExportQuery, TranslationFileResponseBody> {

    private final I18nKeyRepository keys;
    private final TranslationWrites writes;
    private final I18nTranslationRepository translations;
    private final LocalizationProcessorSupport support;

    @Override
    protected TranslationFileResponseBody processRequest(TranslationExportQuery request) {
        support.authorize(request.getActor(), request.getSession(), LocalizationProcessorSupport.VIEW_PERMISSION);
        I18nApplication application = support.requireApplication(request.getApplicationCode());
        writes.requireTranslatable(request.getLocale());
        List<I18nKey> active = keys.findByStatus(application.getApplicationId(), KeyStatus.ACTIVE);
        Map<Long, String> texts = translations.findByKeys(active.stream().map(I18nKey::getKeyId).toList(),
            request.getLocale()).stream().collect(Collectors.toMap(I18nTranslation::getKeyId, I18nTranslation::getText));
        Map<String, Map<String, String>> namespaced = new TreeMap<>();
        for (I18nKey key : active) {
            namespaced.computeIfAbsent(LocalizationCodes.namespaceOf(key.getKeyCode()), ignored -> new TreeMap<>())
                .put(key.getKeyCode(), texts.getOrDefault(key.getKeyId(), ""));
        }
        TranslationFileResponseBody body = new TranslationFileResponseBody();
        body.setFormat(LocalizationCodes.TRANSLATION_FORMAT);
        body.setApplication(application.getApplicationCode());
        body.setLocale(request.getLocale());
        body.setNamespaces(namespaced);
        return body;
    }
}
