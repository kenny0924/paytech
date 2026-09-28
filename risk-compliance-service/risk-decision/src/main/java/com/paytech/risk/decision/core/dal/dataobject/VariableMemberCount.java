package com.paytech.risk.decision.core.dal.dataobject;

/** 集合当前成员数量的数据库聚合投影。 */
public record VariableMemberCount(
    /** 所属集合永久编码。 */
    String variableCode,
    /** 当前提交成员数，包括零条以外的聚合结果。 */
    long memberCount) { }
