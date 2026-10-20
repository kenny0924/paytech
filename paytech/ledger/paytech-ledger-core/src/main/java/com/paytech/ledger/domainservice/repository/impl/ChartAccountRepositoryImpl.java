package com.paytech.ledger.domainservice.repository.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.mapper.ChartAccountMapper;
import com.paytech.ledger.core.enums.ChartAccountStatus;
import com.paytech.ledger.domainservice.repository.ChartAccountRepository;

import lombok.AllArgsConstructor;

/** ChartAccountRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class ChartAccountRepositoryImpl implements ChartAccountRepository {
    /** 科目编号唯一索引名，用于区分编号与代码冲突。 */
    static final String ACCOUNT_NO_INDEX = "uk_chart_acct_no";

    private final Clock clock;
    private final ChartAccountMapper mapper;

    /** 唯一索引兜底：编号冲突与代码冲突分别转为对应错误码。 */
    @Override
    public void insert(ChartAccount account) {
        try {
            mapper.insert(account);
        } catch (DuplicateKeyException e) {
            throw duplicate(e);
        }
    }

    /** 按主键升序读取一组科目；空集合返回空列表。 */
    @Override
    public List<ChartAccount> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.selectByIds(ids);
    }

    /** 按主键读取科目，不存在返回空。 */
    @Override
    public ChartAccount findById(long id) {
        return mapper.selectById(id);
    }

    /** 按科目表和科目代码读取，不存在返回空。 */
    @Override
    public ChartAccount findByChartAndCode(long chartId, String accountCode) {
        return mapper.selectByChartAndCode(chartId, accountCode);
    }

    /** 按科目表和科目编号读取，不存在返回空。 */
    @Override
    public ChartAccount findByChartAndNo(long chartId, String accountNo) {
        return mapper.selectByChartAndNo(chartId, accountNo);
    }

    /** 读取科目表下的科目；postingAllowed、accountStatus 为空时不过滤，按科目编号升序。 */
    @Override
    public List<ChartAccount> findByChart(long chartId, Boolean postingAllowed, ChartAccountStatus accountStatus) {
        return mapper.selectByChart(chartId, postingAllowed, accountStatus);
    }

    /** 读取直接下级科目，按科目编号升序。 */
    @Override
    public List<ChartAccount> findChildren(long parentAccountId) {
        return mapper.selectChildren(parentAccountId);
    }

    /** 行数确认后同步名称、编号、上级与更新时间。 */
    @Override
    public void updateStructure(ChartAccount account, String accountName, String accountNo, Long parentAccountId) {
        Instant now = now();
        int rows;
        try {
            rows = mapper.updateStructure(account, accountName, accountNo, parentAccountId, now);
        } catch (DuplicateKeyException e) {
            throw duplicate(e);
        }
        if (rows != 1) {
            throw new ConcurrentRequestException("科目已被并发修改: " + account.getId());
        }
        account.setAccountName(accountName);
        account.setAccountNo(accountNo);
        account.setParentAccountId(parentAccountId);
        account.setUpdateTime(now);
    }

    /** 行数确认后同步状态与更新时间。 */
    @Override
    public void updateStatus(ChartAccount account, ChartAccountStatus to) {
        Instant now = now();
        if (mapper.updateStatus(account, to, now) != 1) {
            throw new ConcurrentRequestException("科目状态已被并发修改: " + account.getId());
        }
        account.setAccountStatus(to);
        account.setUpdateTime(now);
    }

    /** 按冲突的唯一索引区分编号重复与代码重复。 */
    FailException duplicate(DuplicateKeyException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        LedgerSetupErrorCode code = message.contains(ACCOUNT_NO_INDEX) ? LedgerSetupErrorCode.CHART_ACCOUNT_NO_DUPLICATE
            : LedgerSetupErrorCode.CHART_ACCOUNT_CODE_DUPLICATE;
        return new FailException(code, null, e);
    }

    /** 当前 UTC 时间，截断到数据库支持的微秒精度。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
