package com.paytech.risk.decision.domain.feature;

import java.util.Set;

import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.NumericUnit;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;
import com.paytech.risk.decision.domain.enums.VelocityMetric;
import com.paytech.risk.decision.domain.variable.RiskVariable.Definition;
import com.paytech.risk.decision.domainservice.service.RiskVariableException;

/**
 * 工程注册的 Feature Handler 目录与参数校验
 * <li>实时累计与名单命中是唯一开放的两个 Handler；参数组合在此校验，不接受 SQL 或脚本。</li>
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

    /** 实时累计可锚定或去重的主体。 */
    public static final Set<RiskSubjectType> VELOCITY_SUBJECTS = Set.of(RiskSubjectType.CLIENT,
        RiskSubjectType.OPERATING_ENTITY, RiskSubjectType.CONSUMER, RiskSubjectType.IP_ADDRESS,
        RiskSubjectType.CARD_FINGERPRINT);

    /** 名单命中可判断的主体。 */
    public static final Set<RiskSubjectType> LIST_SUBJECTS = Set.of(RiskSubjectType.CLIENT,
        RiskSubjectType.OPERATING_ENTITY, RiskSubjectType.CONSUMER, RiskSubjectType.IP_ADDRESS,
        RiskSubjectType.CARD_FINGERPRINT, RiskSubjectType.CARD_BIN);

    /** 纯校验工具。 */
    private FeatureHandlers() { }

    /**
     * 校验实时累计参数及其与变量结构的一致性
     *
     * @param spec 累计参数
     * @param definition 变量结构
     *         <li>计数、去重计数、间隔秒数为普通数值；合计与最近金额为金额，币种取本次支付币种变量。</li>
     */
    public static void validate(FeatureSpec.Velocity spec, Definition definition) {
        if (spec.eventCode() == null || spec.metric() == null || !VELOCITY_SUBJECTS.contains(spec.anchorSubjectType())
            || !WINDOWS.contains(spec.windowSeconds())) {
            throw invalid("累计事件、算法、锚定主体或窗口无效");
        }
        boolean distinct = spec.metric() == VelocityMetric.DISTINCT_COUNT;
        if (distinct != (spec.distinctSubjectType() != null)
            || distinct && (!VELOCITY_SUBJECTS.contains(spec.distinctSubjectType())
                || spec.distinctSubjectType() == spec.anchorSubjectType())) {
            throw invalid("去重主体只适用于去重计数，且不能与锚定主体相同");
        }
        boolean money = spec.metric() == VelocityMetric.SUM_AMOUNT || spec.metric() == VelocityMetric.LAST_AMOUNT;
        if (spec.includeCurrent() && (spec.metric() != VelocityMetric.COUNT && spec.metric() != VelocityMetric.SUM_AMOUNT
            || spec.eventCode() != VelocityEventCode.PAYMENT_ATTEMPT_ASSESSED)) {
            throw invalid("只有评估事件的计数和金额合计可以计入本次；评估前尚不知道本次是否被拒绝");
        }
        if (definition.valueType() != DecisionValueType.NUMBER
            || definition.numericUnit() != (money ? NumericUnit.MONEY : NumericUnit.SCALAR)) {
            throw invalid("累计变量的值类型或量纲与算法不一致");
        }
    }

    /**
     * 校验名单命中参数及其与变量结构的一致性
     *
     * @param spec 名单参数
     * @param definition 变量结构，必须为 BOOLEAN
     */
    public static void validate(FeatureSpec.ListMembership spec, Definition definition) {
        if (spec.listCode() == null || !spec.listCode().matches("[A-Z][A-Z0-9_]{0,63}")
            || !LIST_SUBJECTS.contains(spec.subjectType())) {
            throw invalid("名单编码或主体类型无效");
        }
        if (definition.valueType() != DecisionValueType.BOOLEAN) {
            throw invalid("名单命中变量必须为 BOOLEAN");
        }
    }

    /** 构造不含敏感值的结构校验结果。 */
    private static RiskVariableException invalid(String message) {
        return new RiskVariableException(RiskVariableException.Reason.INVALID_VARIABLE_DEFINITION, message);
    }
}
