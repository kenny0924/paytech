package com.paytech.risk.decision.domain.feature;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.NumericUnit;
import com.paytech.risk.decision.domain.enums.VelocityMetric;
import com.paytech.risk.decision.domain.variable.RiskVariable.Definition;
import com.paytech.risk.decision.domainservice.service.RiskVariableException;

/**
 * 工程注册的 Feature Handler 目录与参数校验
 * <li>实时累计与名单命中是唯一开放的两个 Handler；参数组合在此校验，不接受 SQL 或脚本。</li>
 * <li>事件、主体按变量所属风控点限定；Client、运营主体的统计方式与窗口受限（事件量集中，宽窗口查询超出单个 Feature 时限）。</li>
 * <li>只在创建变量时校验，不进入历史变量的完整性复核。</li>
 */
public final class FeatureHandlers {
    /** 实时累计 Handler。 */
    public static final String VELOCITY_HANDLER = "VELOCITY_WINDOW_V1";

    /** 名单命中 Handler。 */
    public static final String LIST_HANDLER = "LIST_MEMBERSHIP_V1";

    /** 两个 Handler 的实现版本。 */
    public static final String IMPLEMENTATION_VERSION = "1.0.0";

    /** 实时累计 Handler 实现摘要：SHA-256("VELOCITY_WINDOW_V1:1.0.0")。 */
    public static final String VELOCITY_HASH = "c770fb666b389ebb54c486b6f128a834224a4dcdcc2f0f263830fa94740001de";

    /** 名单命中 Handler 实现摘要：SHA-256("LIST_MEMBERSHIP_V1:1.0.0")。 */
    public static final String LIST_HASH = "c91efd32ac4e0820ce2bd574bc018ac304fe03fd02e325f77e8fd99a057dd49b";

    /** 单个 Feature 的超时上限，毫秒。 */
    public static final int TIMEOUT_MILLIS = 200;

    /** 一期开放的窗口秒数：10 分钟、1 小时、24 小时、7 天。 */
    public static final Set<Integer> WINDOWS = Set.of(600, 3600, 86_400, 604_800);

    /** 事件量集中的低基数主体：只开放 D1 允许的统计方式与窗口（两个风控点一致）。 */
    private static final Set<RiskSubjectType> CONCENTRATED_SUBJECTS =
        Set.of(RiskSubjectType.CLIENT, RiskSubjectType.OPERATING_ENTITY);

    /** 低基数主体各统计方式的最长窗口秒数；未列出的统计方式（不同主体数）不开放。 */
    private static final Map<VelocityMetric, Integer> CONCENTRATED_MAX_WINDOW = Map.of(
        VelocityMetric.COUNT, 86_400, VelocityMetric.SUM_AMOUNT, 3600,
        VelocityMetric.LAST_AMOUNT, 86_400, VelocityMetric.SECONDS_SINCE_LAST, 86_400);

    /** 纯校验工具。 */
    private FeatureHandlers() { }

    /**
     * 锚定主体某统计方式可选的窗口
     *
     * @param anchor 锚定主体
     * @param metric 统计方式
     * @return 升序窗口秒数；该主体不开放该统计方式时为空
     */
    public static List<Integer> allowedWindows(RiskSubjectType anchor, VelocityMetric metric) {
        if (!CONCENTRATED_SUBJECTS.contains(anchor)) {
            return WINDOWS.stream().sorted().toList();
        }
        Integer max = CONCENTRATED_MAX_WINDOW.get(metric);
        if (max == null) {
            return List.of();
        }
        return WINDOWS.stream().filter(window -> window <= max).sorted().toList();
    }

    /** 统计方式是否可计入本次：只有次数与金额合计。 */
    public static boolean includeCurrentAllowed(VelocityMetric metric) {
        return metric == VelocityMetric.COUNT || metric == VelocityMetric.SUM_AMOUNT;
    }

    /**
     * 校验实时累计参数及其与变量结构的一致性
     *
     * @param spec 累计参数
     * @param definition 变量结构
     *         <li>事件须属于变量风控点；锚定与去重主体须为本点累计主体；统计方式与窗口须为锚定主体允许的组合。</li>
     *         <li>计数、去重计数、间隔秒数为普通数值；合计与最近金额为金额，币种取本点币种变量。</li>
     */
    public static void validate(FeatureSpec.Velocity spec, Definition definition) {
        DecisionPointAuthoringDescriptor point = featurePoint(definition);
        if (spec.eventCode() == null || spec.metric() == null || spec.anchorSubjectType() == null
            || !WINDOWS.contains(spec.windowSeconds())) {
            throw invalid("累计事件、算法、锚定主体或窗口无效");
        }
        if (!spec.eventCode().pointCode().equals(definition.pointCode())) {
            throw invalid("统计事件不属于该决策点");
        }
        if (!point.velocitySubjects().contains(spec.anchorSubjectType())) {
            throw invalid("统计主体不适用于该决策点");
        }
        if (!allowedWindows(spec.anchorSubjectType(), spec.metric()).contains(spec.windowSeconds())) {
            throw invalid("该统计主体不支持所选统计方式或时间窗口");
        }
        validateDistinct(spec, point);
        if (spec.includeCurrent() && (!includeCurrentAllowed(spec.metric()) || !spec.eventCode().includeCurrentAllowed())) {
            throw invalid("只有评估事件的计数和金额合计可以计入本次；评估前尚不知道本次是否被拒绝");
        }
        boolean money = spec.metric() == VelocityMetric.SUM_AMOUNT || spec.metric() == VelocityMetric.LAST_AMOUNT;
        if (definition.valueType() != DecisionValueType.NUMBER
            || definition.numericUnit() != (money ? NumericUnit.MONEY : NumericUnit.SCALAR)) {
            throw invalid("累计变量的值类型或量纲与算法不一致");
        }
    }

    /**
     * 校验名单命中参数及其与变量结构的一致性
     *
     * @param spec 名单参数
     * @param definition 变量结构，必须为 BOOLEAN；名单主体须为本点名单主体
     */
    public static void validate(FeatureSpec.ListMembership spec, Definition definition) {
        DecisionPointAuthoringDescriptor point = featurePoint(definition);
        if (spec.listCode() == null || !spec.listCode().matches("[A-Z][A-Z0-9_]{0,63}") || spec.subjectType() == null) {
            throw invalid("名单编码或主体类型无效");
        }
        if (!point.listSubjects().contains(spec.subjectType())) {
            throw invalid("名单主体不适用于该决策点");
        }
        if (definition.valueType() != DecisionValueType.BOOLEAN) {
            throw invalid("名单命中变量必须为 BOOLEAN");
        }
    }

    /** 去重主体只用于不同主体数，须为本点累计主体且与锚定主体不同。 */
    private static void validateDistinct(FeatureSpec.Velocity spec, DecisionPointAuthoringDescriptor point) {
        boolean distinct = spec.metric() == VelocityMetric.DISTINCT_COUNT;
        if (distinct != (spec.distinctSubjectType() != null)
            || distinct && (!point.velocitySubjects().contains(spec.distinctSubjectType())
                || spec.distinctSubjectType() == spec.anchorSubjectType())) {
            throw invalid("去重主体只适用于不同主体数，且须为该决策点可用主体并与统计主体不同");
        }
    }

    /** 变量所属的、支持 Feature 变量的决策点。 */
    private static DecisionPointAuthoringDescriptor featurePoint(Definition definition) {
        return DecisionPointAuthoringDescriptor.find(definition.pointCode())
            .filter(DecisionPointAuthoringDescriptor::featureVariablesSupported)
            .orElseThrow(() -> invalid("实时累计与名单命中变量必须属于支持 Feature 计算的决策点"));
    }

    /** 构造不含敏感值的结构校验结果。 */
    private static RiskVariableException invalid(String message) {
        return new RiskVariableException(RiskVariableException.Reason.INVALID_VARIABLE_DEFINITION, message);
    }
}
