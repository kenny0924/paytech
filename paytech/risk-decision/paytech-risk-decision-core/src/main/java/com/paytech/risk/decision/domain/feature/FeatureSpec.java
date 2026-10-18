package com.paytech.risk.decision.domain.feature;

import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;
import com.paytech.risk.decision.domain.enums.VelocityMetric;

/** 运营创建实时累计或名单命中变量时提交的 Feature 参数；创建后随 Feature Version 不可变。 */
public sealed interface FeatureSpec permits FeatureSpec.Velocity, FeatureSpec.ListMembership {

    /**
     * 实时累计参数
     *
     * @param eventCode 计入的事件
     * @param anchorSubjectType 锚定主体，取本次评估中该主体的值
     * @param metric 累计算法
     * @param distinctSubjectType 仅 DISTINCT_COUNT：被去重的主体
     * @param windowSeconds 窗口秒数，600、3600、86400 或 604800
     * @param includeCurrent 仅 COUNT、SUM_AMOUNT：是否计入本次
     */
    record Velocity(VelocityEventCode eventCode, RiskSubjectType anchorSubjectType, VelocityMetric metric,
        RiskSubjectType distinctSubjectType, int windowSeconds, boolean includeCurrent) implements FeatureSpec { }

    /**
     * 名单命中参数
     *
     * @param listCode 名单编码
     * @param subjectType 判断的主体，必须与名单支持类型一致
     */
    record ListMembership(String listCode, RiskSubjectType subjectType) implements FeatureSpec { }
}
