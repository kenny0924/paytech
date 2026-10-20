package com.paytech.commercial.domain;

import com.paytech.commercial.api.enums.CommercialTermPlanStatus;
import com.paytech.foundation.web.paging.PagingRequestBody;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 商业条款方案列表查询条件：全部筛选可空。 */
@Getter
@AllArgsConstructor
public class CommercialTermPlanListQuery {
    /** 合同 ID，可空 */
    private final Long agreementId;

    /** Client ID，可空 */
    private final Long clientId;

    /** Local Partner ID，可空 */
    private final Long localPartnerId;

    /** 条款类型（开放目录代码），可空 */
    private final String termType;

    /** 方案状态，可空 */
    private final CommercialTermPlanStatus status;

    /** 已校验的分页参数 */
    @NotNull
    private final PagingRequestBody paging;
}
