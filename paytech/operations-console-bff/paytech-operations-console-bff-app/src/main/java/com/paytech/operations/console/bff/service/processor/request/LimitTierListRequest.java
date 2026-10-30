package com.paytech.operations.console.bff.service.processor.request;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 档位列表的内部请求；筛选已在入口校验格式。 */
@Getter
@AllArgsConstructor
public class LimitTierListRequest {
    /** 入站边界已校验的操作人身份。 */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /** 可选档位。 */
    private final String tier;

    /** 可选：只列有待审批申请的。 */
    private final Boolean pendingApproval;

    /** 可选：只列观察期到期待确认的。 */
    private final Boolean observationDue;

    /** 可选关键字。 */
    private final String keyword;

    /** 已校验的分页。 */
    @NotNull(message = "分页不能为空")
    private final PagingRequestBody paging;
}
