package com.paytech.channel.connector.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.channel.connector.api.candidate.ChannelCandidateQueryApi;
import com.paytech.channel.connector.api.candidate.request.QueryMidCandidatesRequestBody;
import com.paytech.channel.connector.api.candidate.response.QueryMidCandidatesResponseBody;
import com.paytech.channel.connector.ext.rest.converter.QueryMidCandidatesConverter;
import com.paytech.channel.connector.service.processor.QueryMidCandidatesProcessor;

import lombok.AllArgsConstructor;

/** MID 候选查询入口：协议适配后委托 Processor。 */
@RestController
@AllArgsConstructor
public class ChannelCandidateQueryController implements ChannelCandidateQueryApi {
    final QueryMidCandidatesConverter queryMidCandidatesConverter;
    final QueryMidCandidatesProcessor queryMidCandidatesProcessor;

    /** 查询 MID 候选。 */
    @Override
    public QueryMidCandidatesResponseBody queryMidCandidates(QueryMidCandidatesRequestBody requestBody) {
        var candidates = queryMidCandidatesProcessor.process(queryMidCandidatesConverter.convertRequest(requestBody));
        return queryMidCandidatesConverter.convertResponse(candidates);
    }
}
