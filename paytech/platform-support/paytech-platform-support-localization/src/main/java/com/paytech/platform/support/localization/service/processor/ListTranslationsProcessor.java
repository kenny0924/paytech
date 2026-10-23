package com.paytech.platform.support.localization.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.localization.response.TranslationEntryResponseBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.domainservice.repository.I18nKeyRepository;
import com.paytech.platform.support.localization.service.processor.request.TranslationListQuery;

import lombok.AllArgsConstructor;

/**
 * 翻译工作台：复核 I18N_VIEW 后按应用、语言、命名空间、填写状态与关键字分页读取 ACTIVE key 与翻译，不开事务。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListTranslationsProcessor extends AbstractProcessor<TranslationListQuery, PagingResponseBody<TranslationEntryResponseBody>> {

    private final I18nKeyRepository keys;
    private final TranslationWrites writes;
    private final LocalizationQueryLookups lookups;
    private final LocalizationProcessorSupport support;

    @Override
    protected PagingResponseBody<TranslationEntryResponseBody> processRequest(TranslationListQuery request) {
        support.authorize(request.getActor(), request.getSession(), LocalizationProcessorSupport.VIEW_PERMISSION);
        I18nApplication application = support.requireApplication(request.getApplicationCode());
        writes.requireTranslatable(request.getLocale());
        Long namespaceId = lookups.namespaceId(application, request.getNamespaceCode());
        List<TranslationEntryResponseBody> items = keys.findEntries(application.getApplicationId(), request.getLocale(),
            namespaceId, request.getState(), request.getKeyword(), request.getPaging()).stream()
            .map(LocalizationResponses::entry).toList();
        return new PagingResponseBody<>(items, request.getPaging().page(), request.getPaging().size(),
            keys.countEntries(application.getApplicationId(), request.getLocale(), namespaceId, request.getState(),
                request.getKeyword()));
    }
}
