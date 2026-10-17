package com.paytech.ledger.domainservice.repository.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.ledger.core.dal.dataobject.CloseStep;
import com.paytech.ledger.core.dal.mapper.CloseStepMapper;
import com.paytech.ledger.core.enums.CloseStepStatus;
import com.paytech.ledger.domainservice.repository.CloseStepRepository;

import lombok.AllArgsConstructor;

/** CloseStepRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class CloseStepRepositoryImpl implements CloseStepRepository {
    private final Clock clock;
    private final CloseStepMapper mapper;

    /** 批量插入步骤。 */
    @Override
    public void insertBatch(List<CloseStep> steps) {
        mapper.insertBatch(steps);
    }

    /** 按步骤号升序读取流程步骤。 */
    @Override
    public List<CloseStep> findByClose(long periodCloseId) {
        return mapper.selectByClose(periodCloseId);
    }

    /** 把步骤迁移到终态并写入结果摘要，成功后同步原对象。 */
    @Override
    public void complete(CloseStep step, CloseStepStatus status, String resultHash) {
        Instant now = now();
        if (mapper.updateStatus(step, status, resultHash, now) != 1) {
            throw new ConcurrentRequestException("关账步骤状态已变化: " + step.getId());
        }
        step.setStatus(status);
        step.setResultHash(resultHash);
        step.setCompletedTime(now);
        step.setUpdateTime(now);
    }

    /** 当前 UTC 时间，截断到数据库支持的微秒精度。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
