package com.paytech.operations.console.bff.service.processor.request;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 承保列表的内部请求；筛选已在入口校验格式。 */
@Getter
@AllArgsConstructor
public class UnderwritingListRequest {
    /** 入站边界已校验的操作人身份。 */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /** 可选状态。 */
    private final String status;

    /** 可选 CRR。 */
    private final String crr;

    /** 可选结论来源。 */
    private final String decisionSource;

    /** 可选关键字。 */
    private final String keyword;

    /** 可选 Client ID。 */
    private final String clientId;

    /** 只看待当前操作人复核的版本。 */
    private final boolean pendingReviewForMe;

    /** 已校验的分页。 */
    @NotNull(message = "分页不能为空")
    private final PagingRequestBody paging;
}
