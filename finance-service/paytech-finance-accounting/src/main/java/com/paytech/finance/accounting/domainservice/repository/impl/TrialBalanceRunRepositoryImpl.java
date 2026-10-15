package com.paytech.finance.accounting.domainservice.repository.impl;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.finance.accounting.core.dal.dataobject.TrialBalanceRun;
import com.paytech.finance.accounting.core.dal.mapper.TrialBalanceRunMapper;
import com.paytech.finance.accounting.domain.view.TrialBalanceRunView;
import com.paytech.finance.accounting.domainservice.repository.TrialBalanceRunRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** TrialBalanceRunRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class TrialBalanceRunRepositoryImpl implements TrialBalanceRunRepository {
    private final TrialBalanceRunMapper mapper;

    /** 插入终态试算运行；同一账簿期间序号冲突说明并发试算。 */
    @Override
    public void insert(TrialBalanceRun run) {
        try {
            mapper.insert(run);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("同一账簿期间正在并发试算: 账簿 " + run.getLedgerBookId() + ", 期间 " + run.getPeriodId(), e);
        }
    }

    /** 下一个试算序号：当前最大序号加一。 */
    @Override
    public int nextRunNo(long ledgerBookId, long periodId) {
        return mapper.selectMaxRunNo(ledgerBookId, periodId) + 1;
    }

    /** 按序号读取试算摘要，不存在返回空。 */
    @Override
    public TrialBalanceRunView findView(long ledgerBookId, long periodId, int runNo) {
        return mapper.selectView(ledgerBookId, periodId, runNo);
    }

    /** 按序号降序分页读取试算摘要。 */
    @Override
    public List<TrialBalanceRunView> findPage(long ledgerBookId, long periodId, int size, int offset) {
        return mapper.selectPage(ledgerBookId, periodId, size, offset);
    }

    /** 统计账簿期间的试算次数。 */
    @Override
    public long countPage(long ledgerBookId, long periodId) {
        return mapper.countPage(ledgerBookId, periodId);
    }
}
