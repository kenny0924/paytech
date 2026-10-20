package com.paytech.commercial.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;
import com.paytech.foundation.web.paging.PagingRequestBody;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商业条款版本列表查询条件：全部筛选可空，空集合状态表示不按状态筛选
 * <li>窗口 [windowFrom, windowTo) 与版本生效区间 [effective_from, effective_to) 相交，effective_to 为空表示无终点。</li>
 */
@Getter
@AllArgsConstructor
public class CommercialTermVersionListQuery {
    /** 待审批工作台：按提交时刻升序。 */
    public static final String ORDER_SUBMITTED = "SUBMITTED";

    /** 待生效工作台：按生效起点升序。 */
    public static final String ORDER_EFFECTIVE = "EFFECTIVE";

    /** 一般列表：按方案 ID、版本号倒序。 */
    public static final String ORDER_PLAN_VERSION = "PLAN_VERSION";

    /** 方案 ID，可空 */
    private final Long planId;

    /** 合同 ID，可空 */
    private final Long agreementId;

    /** 版本状态集合，空集合不筛选 */
    @NotNull
    private final List<CommercialTermVersionStatus> statuses;

    /** 窗口起点（含），可空 */
    private final Instant windowFrom;

    /** 窗口终点（不含），可空 */
    private final Instant windowTo;

    /** 已校验的分页参数 */
    @NotNull
    private final PagingRequestBody paging;

    /**
     * 排序方式
     * <li>只筛 PENDING_APPROVAL 时按提交时刻（该状态下版本行的 update_time 即提交时刻：提交是进入该状态的唯一写入，审批前不再更新）；
     * 只筛 SCHEDULED 时按生效起点；其他按方案、版本号倒序。</li>
     *
     * @return ORDER_* 之一
     */
    public String order() {
        if (statuses.equals(List.of(CommercialTermVersionStatus.PENDING_APPROVAL))) {
            return ORDER_SUBMITTED;
        }
        if (statuses.equals(List.of(CommercialTermVersionStatus.SCHEDULED))) {
            return ORDER_EFFECTIVE;
        }
        return ORDER_PLAN_VERSION;
    }
}
