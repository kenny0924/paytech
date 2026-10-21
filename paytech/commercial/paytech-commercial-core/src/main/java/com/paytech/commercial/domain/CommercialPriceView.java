package com.paytech.commercial.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponentBand;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;

/**
 * 价格的多表查询结果：父链（方案、版本）、价格与画像条件、组件与档位
 * <li>价格命中、按 ID 回读、版本价格全集与费率卡共用；只承载查询数据。</li>
 */
public class CommercialPriceView {
    private final CommercialTermPlan plan;

    private final CommercialTermVersion version;

    private final CommercialPriceCandidate candidate;

    private final List<CommercialTermComponent> components;

    private final List<CommercialTermComponentBand> bands;

    private final Map<Long, PriceBenchmark> benchmarks;

    /**
     * 组合一条价格的完整内容。
     *
     * @param plan 方案
     * @param version 版本
     * @param candidate 价格、画像与条件
     * @param components 该价格的组件
     * @param bands 这些组件的档位
     */
    public CommercialPriceView(CommercialTermPlan plan, CommercialTermVersion version, CommercialPriceCandidate candidate,
        List<CommercialTermComponent> components, List<CommercialTermComponentBand> bands) {
        this.plan = plan;
        this.version = version;
        this.candidate = candidate;
        this.components = components.stream().sorted(Comparator.comparing(CommercialTermComponent::getComponentNo)).toList();
        this.bands = bands.stream().sorted(Comparator.comparing(CommercialTermComponentBand::getBandNo)).toList();
        this.benchmarks = null;
    }

    /**
     * 同一价格附加组件与来源模板的比较（固定版本价格全集使用）。
     *
     * @param view 价格
     * @param benchmarks 组件 ID → 比较结果
     */
    public CommercialPriceView(CommercialPriceView view, Map<Long, PriceBenchmark> benchmarks) {
        this.plan = view.plan;
        this.version = view.version;
        this.candidate = view.candidate;
        this.components = view.components;
        this.bands = view.bands;
        this.benchmarks = Map.copyOf(benchmarks);
    }

    /** 某组件与来源模板的比较；命中结果与按 ID 回读为 null。 */
    public PriceBenchmark benchmarkOf(CommercialTermComponent component) {
        return benchmarks == null ? null : benchmarks.getOrDefault(component.getTermComponentId(), PriceBenchmark.NONE);
    }

    /** 方案。 */
    public CommercialTermPlan plan() {
        return plan;
    }

    /** 方案是否 Client 条款结构（价格带结算四字段），与保存校验同一判断。 */
    public boolean clientTerm() {
        return CommercialTermVersionContext.clientTerm(plan);
    }

    /** 版本。 */
    public CommercialTermVersion version() {
        return version;
    }

    /** 价格、画像与条件。 */
    public CommercialPriceCandidate candidate() {
        return candidate;
    }

    /** 组件，按 componentNo 升序。 */
    public List<CommercialTermComponent> components() {
        return components;
    }

    /** 某组件的档位，按 bandNo 升序。 */
    public List<CommercialTermComponentBand> bandsOf(CommercialTermComponent component) {
        return bands.stream().filter(band -> band.getTermComponentId().equals(component.getTermComponentId())).toList();
    }
}
