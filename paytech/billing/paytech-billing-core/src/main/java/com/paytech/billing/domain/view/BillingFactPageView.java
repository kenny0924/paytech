package com.paytech.billing.domain.view;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按 factId 游标读取的一页计费事实（按账簿主体或按外部引用）。 */
@Getter
@AllArgsConstructor
public class BillingFactPageView {
    /** 本页事实，按 factId 升序。 */
    private final List<BillingFactView> items;

    /**
     * 下一页游标：本页最后一条的 factId
     * <li>本页不足请求条数时为空。</li>
     */
    private final Long nextFromFactId;
}
