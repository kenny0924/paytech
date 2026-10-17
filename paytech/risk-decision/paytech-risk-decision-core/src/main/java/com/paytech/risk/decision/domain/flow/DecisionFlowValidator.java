package com.paytech.risk.decision.domain.flow;

/** 无数据库、锁或远端调用的图结构与条件校验边界。 */
public interface DecisionFlowValidator {
    DecisionFlow.Checked check(DecisionFlow.Save command);
}
