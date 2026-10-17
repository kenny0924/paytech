package com.paytech.risk.compliance.service.processor.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 审核任务分页结果。 */
@Getter
@AllArgsConstructor
public class ClientReviewCasePageResponse {
    /** 当前页任务摘要。 */
    private final List<ClientReviewCaseResponse> items;
    /** 页码。 */
    private final int page;
    /** 每页条数。 */
    private final int size;
    /** 总数。 */
    private final long total;
}
