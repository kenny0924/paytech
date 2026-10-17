package com.paytech.risk.decision.domain.feature;

import java.util.Arrays;
import java.util.List;

import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;
import com.paytech.risk.decision.domain.enums.VelocityMetric;

/**
 * 一个支持 Feature 变量的风控点的可选目录；由工程登记投影，供变量创作页使用
 * <li>与创建校验同源：事件取 {@link VelocityEventCode#of(String)}，主体取描述符，统计方式与窗口取 {@link FeatureHandlers#allowedWindows}。</li>
 * <li>不替代变量库与名单库查询。</li>
 *
 * @param pointCode 风控点编码
 * @param currencyVariableCode 金额类累计变量绑定的本点币种输入变量
 * @param velocityEvents 本点累计事件
 * @param velocityAnchors 可锚定主体及其可选统计方式
 * @param distinctSubjects 可作为去重对象的主体；没有锚定主体开放不同主体数时为空
 * @param listSubjects 名单命中可选的主体
 */
public record FeatureCatalog(String pointCode, String currencyVariableCode, List<VelocityEventCode> velocityEvents,
    List<Anchor> velocityAnchors, List<RiskSubjectType> distinctSubjects, List<RiskSubjectType> listSubjects) {

    /**
     * 投影风控点目录
     *
     * @param point 支持 Feature 变量的风控点
     * @return 目录
     */
    public static FeatureCatalog of(DecisionPointAuthoringDescriptor point) {
        String pointCode = point.point().pointCode();
        List<Anchor> anchors = point.velocitySubjects().stream().map(FeatureCatalog::anchor).toList();
        boolean distinct = anchors.stream().anyMatch(a -> a.metrics().stream()
            .anyMatch(m -> m.metric() == VelocityMetric.DISTINCT_COUNT));
        return new FeatureCatalog(pointCode, point.currencyVariableCode(), VelocityEventCode.of(pointCode), anchors,
            distinct ? point.velocitySubjects() : List.of(), point.listSubjects());
    }

    /** 锚定主体可选的统计方式，按算法声明顺序，省略不开放的统计方式。 */
    static Anchor anchor(RiskSubjectType subject) {
        List<Metric> metrics = Arrays.stream(VelocityMetric.values())
            .map(metric -> new Metric(metric, FeatureHandlers.allowedWindows(subject, metric),
                FeatureHandlers.includeCurrentAllowed(metric)))
            .filter(metric -> !metric.windowSeconds().isEmpty()).toList();
        return new Anchor(subject, metrics);
    }

    /**
     * 可锚定主体
     *
     * @param subjectType 主体类型
     * @param metrics 可选统计方式
     */
    public record Anchor(RiskSubjectType subjectType, List<Metric> metrics) { }

    /**
     * 统计方式及其可选窗口
     *
     * @param metric 统计方式
     * @param windowSeconds 升序窗口秒数
     * @param includeCurrentAllowed 是否可计入本次（还须事件允许）
     */
    public record Metric(VelocityMetric metric, List<Integer> windowSeconds, boolean includeCurrentAllowed) { }
}
