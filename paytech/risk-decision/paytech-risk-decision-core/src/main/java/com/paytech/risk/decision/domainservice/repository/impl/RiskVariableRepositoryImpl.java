package com.paytech.risk.decision.domainservice.repository.impl;

import static com.paytech.risk.decision.domainservice.service.RiskVariableException.Reason.*;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.*;
import com.paytech.risk.decision.core.dal.mapper.RiskVariableMapper;
import com.paytech.risk.decision.domain.variable.RiskVariable.Query;
import com.paytech.risk.decision.domainservice.repository.RiskVariableRepository;
import com.paytech.risk.decision.domainservice.service.RiskVariableException;
import java.sql.SQLException;
import java.util.List;
import java.time.Instant;
import com.paytech.risk.decision.domain.variable.VariableChange;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** 维护变量根表及数据库最终防重。 */
@Repository
@AllArgsConstructor
public class RiskVariableRepositoryImpl implements RiskVariableRepository {
    private final RiskVariableMapper mapper;
    /** 查询系统来源的有限兼容投影。 */
    @Override public List<RiskVariableDefinition> systemDefinitions(String pointCode) { return mapper.systemDefinitions(pointCode); }

    /** 读取根及调用者选择的数据库锁模式。 */
    @Override public RiskVariableDefinition find(String code, String lock) { return mapper.find(code, lock); }
    /** 在数据库执行稳定排序与分页，不读取整库再截取。 */
    @Override public List<RiskVariableDefinition> list(Query query, int limit, int offset) { return mapper.list(query, limit, offset); }
    /** 统计与列表完全相同的筛选条件。 */
    @Override public long count(Query query) { return mapper.count(query); }

    /** 只将永久编码的唯一冲突作为可重试业务结果，其他标识冲突保留为系统错误。 */
    @Override public void insert(RiskVariableDefinition value) {
        try {
            if (mapper.insertVariable(value) != 1) { throw new ErrorException("变量创建未写入一行"); }
        } catch (DuplicateKeyException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql && sql.getErrorCode() == 1062
                    && sql.getMessage() != null && sql.getMessage().contains("uk_risk_variable_code")) {
                    throw new RiskVariableException(VARIABLE_CODE_EXISTS, "变量编码已存在且不能复用");
                }
            }
            throw new ErrorException("变量主键冲突", failure);
        }
    }

    /** 更新按数据库原版本执行 CAS，不自动采用用户未确认的新版本。 */
    @Override public void update(RiskVariableDefinition root, VariableChange change, long actorId, Instant time) {
        if (mapper.update(root, change, actorId, time) != 1) {
            throw new RiskVariableException(VARIABLE_VERSION_CONFLICT, "变量已改变，请刷新后确认");
        }
        root.apply(change, actorId, time);
    }

}
