package com.paytech.platform.support.ext.rest.referencedata;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.referencedata.MccQueryApi;
import com.paytech.platform.support.api.referencedata.response.MccResponseBody;
import com.paytech.platform.support.referencedata.service.processor.SearchMccsProcessor;

import lombok.AllArgsConstructor;

/** MCC 分页搜索的协议适配；不开事务。 */
@RestController
@AllArgsConstructor
public class MccController implements MccQueryApi {

    private final SearchMccsProcessor processor;

    private final ReferenceDataQueryConverter converter;

    /** 按可选关键字、编码与状态分页搜索。 */
    @Override
    public PagingResponseBody<MccResponseBody> searchMccs(String keyword, String codes, String status, Integer page,
        Integer size) {
        return converter.mccPage(processor.process(converter.mccs(keyword, codes, status, page, size)));
    }
}
