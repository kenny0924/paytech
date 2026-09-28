package com.paytech.risk.decision.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.core.dal.dataobject.DecisionPointVersion;
import com.paytech.risk.decision.core.dal.mapper.DecisionRuleSetMapper;
import com.paytech.risk.decision.domain.enums.DecisionPointVersionStatus;
import com.paytech.risk.decision.domainservice.repository.DecisionPointVersionRepository;
import lombok.AllArgsConstructor;

/** 复用旧版本查询 SQL，保留顺序、边界和数据映射。 */
@Repository
@AllArgsConstructor
public class DecisionPointVersionRepositoryImpl implements DecisionPointVersionRepository {
    private final DecisionRuleSetMapper mapper;

    /** 只查询本表，历史解释由调用方负责。 */
    @Override
    public Optional<DecisionPointVersion> findById(long id) { return Optional.ofNullable(mapper.selectPointVersion(id)); }
    /** 批量查询保留原 SQL 的坐标与稳定顺序。 */
    @Override
    public List<DecisionPointVersion> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : List.copyOf(mapper.selectPointVersionsByIds(ids));
    }

    /** 只改变已锁定本表版本，成功后同步其内存状态。 */
    @Override
    public void retire(DecisionPointVersion value, Instant time) {
        requireCas(mapper.retirePointVersion(value.pointVersionId(), DecisionPointVersionStatus.DRAFT, time));
        value.markRetired(time);
    }

    /** 内容 Hash 和 DRAFT 状态共同保护冻结。 */
    @Override
    public void freeze(DecisionPointVersion value, Instant time) {
        requireCas(mapper.freezePointVersion(value.pointVersionId(), DecisionPointVersionStatus.DRAFT, value.contentHash(), time));
        value.markFrozen(time);
    }

    /** 旧协议的锁后 CAS 必须恰好影响一行。 */
    private static void requireCas(int affected) {
        if (affected != 1) throw new ErrorException("版本状态更新受影响行数异常: actual=" + affected);
    }
}
