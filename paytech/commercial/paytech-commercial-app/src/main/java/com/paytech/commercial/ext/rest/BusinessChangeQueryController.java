package com.paytech.commercial.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.commercial.api.history.BusinessChangeQueryApi;
import com.paytech.commercial.api.history.response.BusinessChangeSummary;
import com.paytech.commercial.service.processor.BusinessChangeHistoryQueryProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** 变更历史的 HTTP 协议适配：参数转换后委托 BusinessChangeHistoryQueryProcessor。 */
@RestController
@AllArgsConstructor
public class BusinessChangeQueryController implements BusinessChangeQueryApi {
    private final BusinessChangeHistoryQueryProcessor businessChangeHistoryQueryProcessor;

    /** 按聚合根分页读取变更快照。 */
    @Override
    public PagingResponseBody<BusinessChangeSummary> listChanges(String aggregateRootType, String aggregateRootId,
        Integer page, Integer size) {
        return businessChangeHistoryQueryProcessor.process(
            BusinessChangeConverter.query(aggregateRootType, aggregateRootId, page, size))
            .map(BusinessChangeConverter::summary);
    }
}
