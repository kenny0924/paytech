package com.paytech.finance.accounting.domainservice.repository.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.finance.accounting.core.dal.dataobject.PeriodCloseBook;
import com.paytech.finance.accounting.core.dal.mapper.PeriodCloseBookMapper;
import com.paytech.finance.accounting.core.enums.CloseBookStatus;
import com.paytech.finance.accounting.domainservice.repository.PeriodCloseBookRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** PeriodCloseBookRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class PeriodCloseBookRepositoryImpl implements PeriodCloseBookRepository {
    private final Clock clock;
    private final PeriodCloseBookMapper mapper;

    /** 批量插入账簿证据。 */
    @Override
    public void insertBatch(List<PeriodCloseBook> books) {
        mapper.insertBatch(books);
    }

    /** 按账簿主键升序读取流程的账簿证据。 */
    @Override
    public List<PeriodCloseBook> findByClose(long periodCloseId) {
        return mapper.selectByClose(periodCloseId);
    }

    /** 记录结转分录水位与指令，成功后同步原对象。 */
    @Override
    public void recordPosting(PeriodCloseBook book, long asOfSequence, long instructionId) {
        Instant now = now();
        if (mapper.recordPosting(book, asOfSequence, instructionId, now) != 1) {
            throw new ConcurrentRequestException("关账账簿证据状态已变化: " + book.getId());
        }
        book.setAsOfSequence(asOfSequence);
        book.setPostingInstructionId(instructionId);
        book.setUpdateTime(now);
    }

    /** 冻结最终试算并置为 FINALIZED，成功后同步原对象。 */
    @Override
    public void finalizeBook(PeriodCloseBook book, long finalTrialRunId) {
        Instant now = now();
        if (mapper.finalizeBook(book, finalTrialRunId, now) != 1) {
            throw new ConcurrentRequestException("关账账簿证据状态已变化: " + book.getId());
        }
        book.setFinalTrialRunId(finalTrialRunId);
        book.setStatus(CloseBookStatus.FINALIZED);
        book.setUpdateTime(now);
    }

    /** 统计不满足最终冻结条件的账簿数。 */
    @Override
    public long countInvalidFinalBooks(long periodCloseId) {
        return mapper.countInvalidFinalBooks(periodCloseId);
    }

    /** 当前 UTC 时间，截断到数据库支持的微秒精度。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
