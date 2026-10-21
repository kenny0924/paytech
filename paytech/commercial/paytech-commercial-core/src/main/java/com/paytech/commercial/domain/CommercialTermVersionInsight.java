package com.paytech.commercial.domain;

import java.util.List;
import java.util.Map;

import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.core.dal.dataobject.TermVersionAcceptance;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 版本读取时按当前事实计算的附加信息：破标、标准价已更新、客户签署与价格相关性（technique.md §4.5）
 * <li>多表组合查询结果，不参与写入；Client 合同方案之外的相关性提示为空列表。</li>
 */
@Getter
@AllArgsConstructor
public class CommercialTermVersionInsight {
    /** 没有任何附加信息。 */
    public static final CommercialTermVersionInsight NONE = new CommercialTermVersionInsight(0, null, null, List.of(), Map.of());

    /** 破标组件数 */
    private final int belowStandardCount;

    /** 来源模板方案的较新生效版本，没有为空 */
    private final CommercialTermVersion templateUpdate;

    /** 客户签署记录，没有为空 */
    private final TermVersionAcceptance acceptance;

    /** 与合同开通项不相关的价格 */
    private final List<PriceRelevanceIssue> relevanceIssues;

    /** 组件 ID → 与来源模板的比较 */
    private final Map<Long, PriceBenchmark> benchmarks;
}
