package com.paytech.finance.accounting.domainservice.repository.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Repository;

import com.paytech.finance.accounting.core.dal.dataobject.BookSequence;
import com.paytech.finance.accounting.core.dal.mapper.BookSequenceMapper;
import com.paytech.finance.accounting.domainservice.repository.BookSequenceRepository;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** BookSequenceRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class BookSequenceRepositoryImpl implements BookSequenceRepository {
    private final Clock clock;
    private final BookSequenceMapper mapper;

    /** 插入账簿序号行。 */
    @Override
    public void insert(BookSequence sequence) {
        mapper.insert(sequence);
    }

    /** 以排他锁读取账簿序号行；必须在事务内调用。 */
    @Override
    public BookSequence lockByLedgerBook(long ledgerBookId) {
        return mapper.selectByLedgerBookForUpdate(ledgerBookId);
    }

    /** 把已锁定的序号推进一位，成功后同步原对象。 */
    @Override
    public void advance(BookSequence sequence) {
        Instant now = now();
        if (mapper.advance(sequence, now) != 1) {
            throw new ErrorException("账簿序号行在持锁期间被修改: " + sequence.getLedgerBookId());
        }
        sequence.setNextValue(sequence.getNextValue() + 1);
        sequence.setUpdateTime(now);
    }

    /** 当前 UTC 时间，截断到数据库支持的微秒精度。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
