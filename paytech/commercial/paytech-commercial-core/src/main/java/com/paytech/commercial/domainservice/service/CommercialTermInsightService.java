package com.paytech.commercial.domainservice.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPrice;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.CommercialTermVersionInsight;
import com.paytech.commercial.domain.CommercialTermVersionView;
import com.paytech.commercial.domain.PriceBenchmark;

/**
 * 商业条款版本的读取附加信息（开通与定价模型调整 technique.md §4.5）：破标、标准价已更新、客户签署与按当前开通状态计算的价格相关性
 * <li>只读本库，不加锁、不开事务；写入命令的响应与版本详情、列表共用。</li>
 */
public interface CommercialTermInsightService {
    /**
     * 单个版本的完整附加信息
     *
     * @param view 方案与版本
     * @return 附加信息；方案为空版本时为 NONE
     */
    CommercialTermVersionInsight describe(CommercialTermVersionView view);

    /**
     * 列表行的破标数与签署情况
     *
     * @param versions 当前页版本
     * @return 版本 ID → 附加信息（只含破标数与签署记录）
     */
    Map<Long, CommercialTermVersionInsight> summarize(Collection<CommercialTermVersion> versions);

    /**
     * 一组价格的组件与来源模板比较
     *
     * @param prices 价格
     * @param components 这些价格的组件
     * @return 组件 ID → 比较结果；价格没有来源模板时为 NONE
     */
    Map<Long, PriceBenchmark> benchmarks(List<CommercialTermPrice> prices, List<CommercialTermComponent> components);
}
