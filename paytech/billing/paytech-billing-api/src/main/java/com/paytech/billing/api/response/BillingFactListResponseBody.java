package com.paytech.billing.api.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 计费事实列表的一页（factId 游标分页），用于按账簿主体与按引用查询。 */
@Getter
@Setter
public class BillingFactListResponseBody {
    /** 本页事实，按 factId 升序；按账簿主体查询只含 APPROVED 与 SUPERSEDED，按引用查询含任意状态 */
    private List<BillingFactResponseBody> items;

    /**
     * 下一页游标
     * <li>本页最后一条的 factId；本页不足 limit 条时为空，表示已读完。</li>
     */
    private String nextFromFactId;
}
