package com.paytech.risk.decision.domainservice.repository.impl;

import com.paytech.risk.decision.core.dal.dataobject.*;
import com.paytech.risk.decision.core.dal.mapper.RiskVariableReferenceMapper;
import com.paytech.risk.decision.domainservice.repository.RiskVariableReferenceRepository;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

/** 固定 SQL 扫描适配，不缓存或写入影子引用。 */
@Repository
@AllArgsConstructor
public class RiskVariableReferenceRepositoryImpl implements RiskVariableReferenceRepository {
    private final RiskVariableReferenceMapper mapper;
    /** 读取当前快照内各阶段上界。 */
    @Override public List<VariableReferenceBound> bounds() { return mapper.bounds(); }
    /** 委托固定 SQL；阶段值由领域游标校验后传入。 */
    @Override public List<VariableReferenceRow> scan(String phase, String code, String scope, long lastId, long upperBound, int limit) {
        return mapper.scan(phase, code, scope, lastId, upperBound, limit);
    }
}
