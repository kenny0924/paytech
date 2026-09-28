package com.paytech.risk.decision.domainservice.repository;

import com.paytech.risk.decision.core.dal.dataobject.*;
import java.util.List;

/** 同库变量反向引用的有界扫描持久化接口。 */
public interface RiskVariableReferenceRepository {
    /** 获取一次遍历的初始上界。 */
    List<VariableReferenceBound> bounds();
    /** 按精确阶段和变量读取候选。 */
    List<VariableReferenceRow> scan(String phase, String code, String scope, long lastId, long upperBound, int limit);
}
