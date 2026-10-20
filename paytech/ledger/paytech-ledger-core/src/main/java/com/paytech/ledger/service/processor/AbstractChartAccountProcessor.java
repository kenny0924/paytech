package com.paytech.ledger.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.constant.AccountingLocks;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.dataobject.ChartAccountChange;
import com.paytech.ledger.core.dal.dataobject.ChartOfAccounts;
import com.paytech.ledger.core.enums.ChartAccountChangeType;
import com.paytech.ledger.core.enums.ChartAccountStatus;
import com.paytech.ledger.core.enums.ReferenceDataStatus;
import com.paytech.ledger.domainservice.repository.ChartAccountRepository;
import com.paytech.ledger.domainservice.repository.ChartOfAccountsRepository;

/**
 * 科目维护四个写入口（新增、修改、停用、启用）共用的锁与查询步骤
 * <li>同一科目表的维护与新建账簿共用 {@link AccountingLocks#chart(long)}（LOCK）：锁内“检查未采用 → 修改结构”与采用互斥。</li>
 * <li>科目表必须存在且 ACTIVE；被维护的科目必须属于路径科目表。</li>
 * <li>维护不审批、直接生效；每次实际变化追加一条变更记录，操作人取自 X-Gateway-Actor-Subject。</li>
 */
public abstract class AbstractChartAccountProcessor<Request> extends AbstractLockProcessor<Request, ChartAccount> {
    final Clock clock;
    final IdGenerator idGenerator;
    final ChartAccountRepository chartAccountRepository;
    final ChartOfAccountsRepository chartOfAccountsRepository;

    /** 注入四个维护入口共用的依赖。 */
    AbstractChartAccountProcessor(Clock clock, IdGenerator idGenerator, ChartAccountRepository chartAccountRepository,
        ChartOfAccountsRepository chartOfAccountsRepository) {
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.chartAccountRepository = chartAccountRepository;
        this.chartOfAccountsRepository = chartOfAccountsRepository;
    }

    /** 请求指定的科目表代码。 */
    protected abstract String chartCode(Request request);

    /** 科目表资源锁持续等待；锁 Key 由科目表主键构造。 */
    @Override
    protected LockCommand lockCommand(Request request) {
        return LockCommand.lock(AccountingLocks.chart(getActiveChart(chartCode(request)).getId()));
    }

    /** 科目表必须存在且 ACTIVE。 */
    ChartOfAccounts getActiveChart(String chartCode) {
        ChartOfAccounts chart = chartOfAccountsRepository.findByChartCode(chartCode);
        if (chart == null || chart.getStatus() != ReferenceDataStatus.ACTIVE) {
            throw new FailException(LedgerSetupErrorCode.CHART_NOT_ACTIVE);
        }
        return chart;
    }

    /** 科目必须存在且属于该科目表。 */
    ChartAccount getAccount(ChartOfAccounts chart, long chartAccountId) {
        ChartAccount account = chartAccountRepository.findById(chartAccountId);
        if (account == null || !account.getChartId().equals(chart.getId())) {
            throw new FailException(LedgerSetupErrorCode.CHART_ACCOUNT_NOT_FOUND);
        }
        return account;
    }

    /**
     * 解析上级科目：必须是同表、启用、类别相同的汇总科目
     *
     * @param parentAccountNo 上级科目编号；为空表示一级科目，返回空
     * @throws FailException 不满足时 CHART_PARENT_INVALID
     */
    ChartAccount getParent(ChartOfAccounts chart, String parentAccountNo, ChartAccount child) {
        if (parentAccountNo == null) {
            return null;
        }
        ChartAccount parent = chartAccountRepository.findByChartAndNo(chart.getId(), parentAccountNo);
        if (parent == null || Boolean.TRUE.equals(parent.getPostingAllowed())
            || parent.getAccountStatus() != ChartAccountStatus.ACTIVE
            || parent.getAccountClass() != child.getAccountClass()) {
            throw new FailException(LedgerSetupErrorCode.CHART_PARENT_INVALID,
                "上级科目不存在、不是汇总科目、已停用或类别不同: " + parentAccountNo);
        }
        return parent;
    }

    /** 以科目当前值为变更前、调用方随后填写变更后的值构造变更记录；CREATE 不带变更前的值。 */
    ChartAccountChange newChange(ChartAccount account, ChartAccountChangeType type, String operatorId) {
        ChartAccountChange change = new ChartAccountChange();
        change.setId(idGenerator.nextId());
        change.setChartId(account.getChartId());
        change.setChartAccountId(account.getId());
        change.setChangeType(type);
        if (type != ChartAccountChangeType.CREATE) {
            change.setBeforeAccountNo(account.getAccountNo());
            change.setBeforeParentAccountId(account.getParentAccountId());
            change.setBeforeAccountName(account.getAccountName());
            change.setBeforeAccountStatus(account.getAccountStatus());
        }
        change.setAfterAccountNo(account.getAccountNo());
        change.setAfterParentAccountId(account.getParentAccountId());
        change.setAfterAccountName(account.getAccountName());
        change.setAfterAccountStatus(account.getAccountStatus());
        change.setOperatorId(operatorId);
        change.setChangeTime(now());
        return change;
    }

    /** 当前 UTC 时间，截断到数据库支持的微秒精度。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
