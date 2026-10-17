package com.paytech.channel.connector.api.candidate.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 候选查询结果；没有匹配的启用 MID 时为空列表，属正常业务结果。 */
@Getter
@Setter
public class QueryMidCandidatesResponseBody {
    /** 按渠道账号与 MID 读取顺序排列的候选。 */
    private List<MidCandidateBody> candidates;
}
