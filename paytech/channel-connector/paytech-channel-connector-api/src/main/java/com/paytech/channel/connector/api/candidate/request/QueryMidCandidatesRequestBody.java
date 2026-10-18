package com.paytech.channel.connector.api.candidate.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 查询某 Operating Entity 在本次交易维度下可用的 MID 候选。 */
@Getter
@Setter
public class QueryMidCandidatesRequestBody {
    /** 交易已冻结的 Operating Entity 编号，区分大小写；只返回该 OE 的账号与 MID。 */
    @NotNull
    @Pattern(regexp = "[0-9A-Za-z]{5}")
    private String entityNo;

    /** 本次候选检索的完整交易维度。 */
    @NotNull
    @Valid
    private MidCriteriaBody criteria;
}
