package com.paytech.platform.support.localization.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.localization.response.LocaleListResponseBody;
import com.paytech.platform.support.localization.domainservice.repository.I18nLocaleRepository;
import com.paytech.platform.support.localization.service.processor.request.OperatorQuery;

import lombok.AllArgsConstructor;

/**
 * 全部语言：复核 I18N_VIEW 后读取，按排序值、语言代码升序；不开事务。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListLocalesProcessor extends AbstractProcessor<OperatorQuery, LocaleListResponseBody> {

    private final I18nLocaleRepository locales;
    private final LocalizationProcessorSupport support;

    @Override
    protected LocaleListResponseBody processRequest(OperatorQuery request) {
        support.authorize(request.getActor(), request.getSession(), LocalizationProcessorSupport.VIEW_PERMISSION);
        LocaleListResponseBody body = new LocaleListResponseBody();
        body.setItems(locales.findAll().stream().map(LocalizationResponses::locale).toList());
        return body;
    }
}
