package com.paytech.billing.domain.view;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按账簿主体游标读取的一页已批准经济事实。 */
@Getter
@AllArgsConstructor
public class EconomicFactPageView {
    /** 本页事实，按 factId 升序。 */
    private final List<EconomicFactView> items;

    /**
     * 下一页游标
     * <li>本页不足请求条数时为空。</li>
     */
    private final Long nextFromFactId;
}
