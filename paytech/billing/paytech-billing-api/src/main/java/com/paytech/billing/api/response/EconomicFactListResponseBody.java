package com.paytech.billing.api.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 按账簿主体顺序读取已批准经济事实的一页（游标分页）。 */
@Getter
@Setter
public class EconomicFactListResponseBody {
    /** 本页事实，按 factId 升序；只含 APPROVED 与 SUPERSEDED */
    private List<EconomicFactResponseBody> items;

    /**
     * 下一页游标
     * <li>本页最后一条的 factId；本页不足 limit 条时为空，表示已读完。</li>
     */
    private String nextFromFactId;
}
