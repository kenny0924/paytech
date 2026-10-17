package com.paytech.channel.connector.service.processor.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/** Channel 内部业务合同。 */
@Getter
@Setter
public class QueryMidCandidatesRequest {
    /** 交易已冻结的 CCT OE 编号，区分大小写。 */
    @NotNull @Pattern(regexp = "[0-9A-Za-z]{5}")
    private String entityNo;

    /** 本次候选检索的完整交易维度。 */
    @NotNull @Valid
    private MidCriteria criteria;
}
