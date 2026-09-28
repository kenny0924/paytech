package com.paytech.risk.decision.domainservice.service;

import com.paytech.risk.decision.domain.variable.VariableReferences.Page;

/** 变量全部版本的使用关系以及删除前的完整存在性复核。 */
public interface RiskVariableReferenceService {
    /** 每页最多扫描一千候选，可能返回空页且允许继续。 */
    Page page(String code, String scope, String cursor, int size);
    /** 必须在变量根写锁之后调用；发现任一引用即返回 true。 */
    boolean hasReferences(String code);
}
