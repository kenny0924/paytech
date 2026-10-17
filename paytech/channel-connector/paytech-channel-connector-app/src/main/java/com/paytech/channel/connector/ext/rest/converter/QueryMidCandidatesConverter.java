package com.paytech.channel.connector.ext.rest.converter;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.candidate.request.QueryMidCandidatesRequestBody;
import com.paytech.channel.connector.api.candidate.response.MidCandidateBody;
import com.paytech.channel.connector.api.candidate.response.QueryMidCandidatesResponseBody;
import com.paytech.channel.connector.domain.MidCandidateView;
import com.paytech.channel.connector.service.processor.request.QueryMidCandidatesRequest;

/** 候选查询协议转换：ID 与金额一律十进制字符串，不暴露逐代码能力明细。 */
@Component
public class QueryMidCandidatesConverter {
    /** 转换请求。 */
    public QueryMidCandidatesRequest convertRequest(QueryMidCandidatesRequestBody body) {
        var request = new QueryMidCandidatesRequest();
        request.setEntityNo(body.getEntityNo());
        request.setCriteria(MidCriteriaConverter.convert(body.getCriteria()));
        return request;
    }

    /** 转换响应。 */
    public QueryMidCandidatesResponseBody convertResponse(List<MidCandidateView> candidates) {
        var body = new QueryMidCandidatesResponseBody();
        body.setCandidates(candidates.stream().map(QueryMidCandidatesConverter::candidate).toList());
        return body;
    }

    /** 单个候选。 */
    static MidCandidateBody candidate(MidCandidateView view) {
        var body = new MidCandidateBody();
        body.setChannelId(view.getChannelId().toString());
        body.setChannelAccountId(view.getChannelAccountId().toString());
        body.setMidId(view.getMidId().toString());
        body.setSupportedOperations(List.copyOf(view.getSupportedOperations()));
        body.setMinAmount(text(view.getMinAmount()));
        body.setMaxAmount(text(view.getMaxAmount()));
        return body;
    }

    /** 精确十进制文本，不舍入。 */
    static String text(BigDecimal amount) {
        return amount == null ? null : amount.toPlainString();
    }
}
