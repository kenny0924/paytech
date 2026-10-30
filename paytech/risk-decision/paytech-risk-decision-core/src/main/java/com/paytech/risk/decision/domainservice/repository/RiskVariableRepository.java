package com.paytech.risk.decision.domainservice.repository;

import com.paytech.risk.decision.core.dal.dataobject.*;
import com.paytech.risk.decision.domain.variable.RiskVariable.Query;
import java.util.List;
import java.time.Instant;
import com.paytech.risk.decision.domain.variable.VariableChange;

/** 变量根表的持久化边界；数据对象仅在 decision Owner JAR 内使用。 */
public interface RiskVariableRepository {
    /** 读取有限的系统输入/节点输出定义，供原合同字段对照。 */
    List<RiskVariableDefinition> systemDefinitions(String pointCode);
    /** 读取当前根；WRITE/SHARE 只在调用者本地事务内使用。 */
    RiskVariableDefinition find(String code, String lock);
    /** 读取编码以给定前缀开头的 ACTIVE 常量与集合根，按编码排序。 */
    List<RiskVariableDefinition> activeConstantsByCodePrefix(String prefix);
    /** 稳定分页读取符合条件的根。 */
    List<RiskVariableDefinition> list(Query query, int limit, int offset);
    /** 返回相同筛选范围的总数。 */
    long count(Query query);
    /** 创建永久编码，并将唯一键冲突转换为业务结果。 */
    void insert(RiskVariableDefinition value);
    /** 按期望版本更新根，影响行数必须为一。 */
    void update(RiskVariableDefinition root, VariableChange change, long actorId, Instant time);
}
