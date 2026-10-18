package com.paytech.channel.connector.api.candidate;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.channel.connector.api.candidate.request.QueryMidCandidatesRequestBody;
import com.paytech.channel.connector.api.candidate.response.QueryMidCandidatesResponseBody;

import jakarta.validation.Valid;

/**
 * MID 候选查询（服务发现名 {@code channel-connector-service}，无 context path）。
 * <li>只读配置，不做最终选路；Payment 在事务外调用后自行选择并冻结三元组。</li>
 * <li>配置损坏（Channel/OE 关系不一致、能力冲突或未注册）返回 500，不伪装成空候选；请求校验失败返回 422。</li>
 */
@HttpExchange("/channel-connector/v1")
public interface ChannelCandidateQueryApi {
    /**
     * 查询 MID 候选
     *
     * @param requestBody OE 与交易维度
     * @return 候选列表
     */
    @PostExchange("/mid-candidates")
    QueryMidCandidatesResponseBody queryMidCandidates(@Valid @RequestBody QueryMidCandidatesRequestBody requestBody);
}
