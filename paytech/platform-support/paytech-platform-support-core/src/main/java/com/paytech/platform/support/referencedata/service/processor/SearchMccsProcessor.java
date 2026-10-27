package com.paytech.platform.support.referencedata.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.referencedata.domainservice.repository.MccRepository;
import com.paytech.platform.support.referencedata.service.processor.request.SearchMccsRequest;
import com.paytech.platform.support.referencedata.service.processor.response.MccResult;

import lombok.AllArgsConstructor;

/** 分页搜索 MCC，按编码升序；只读，不开事务。 */
@Component
@AllArgsConstructor
public class SearchMccsProcessor extends AbstractProcessor<SearchMccsRequest, PagingResponseBody<MccResult>> {

    private final MccRepository repository;

    @Override
    protected PagingResponseBody<MccResult> processRequest(SearchMccsRequest request) {
        return repository.search(request.getKeyword(), request.getStatus(), request.getCodes(), request.getPaging())
            .map(MccResult::of);
    }
}
