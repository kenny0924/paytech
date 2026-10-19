package com.paytech.ledger.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.ledger.core.dal.dataobject.AccountingPeriod;
import com.paytech.ledger.core.dal.dataobject.CloseStep;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.core.dal.dataobject.PeriodClose;
import com.paytech.ledger.core.dal.dataobject.PeriodCloseBook;
import com.paytech.ledger.core.dal.dataobject.TrialBalanceRun;
import com.paytech.ledger.core.enums.CloseStepType;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 一次关账：期间、账簿、流程根、账簿证据、步骤，以及执行中产生的最终试算摘要。 */
@Getter
@RequiredArgsConstructor
public class PeriodCloseContext {
    /** 被关账的期间；状态迁移成功后由 Repository 同步。 */
    private final AccountingPeriod period;

    /** 期间主体的全部账簿，按主键升序。 */
    private final List<LedgerBook> books;

    /** 账簿主键 → 币种小数位。 */
    private final Map<Long, Integer> minorUnits;

    /** 流程根。 */
    private final PeriodClose close;

    /** 账簿证据，与账簿一一对应。 */
    private final List<PeriodCloseBook> closeBooks;

    /** 7 个固定步骤，按步骤号升序。 */
    private final List<CloseStep> steps;

    /** 账簿主键 → 步骤 4 生成的试算；步骤 5 读取。 */
    private final Map<Long, TrialBalanceRun> trialRuns = new HashMap<>();

    /** 账簿主键 → 最终试算内容摘要；步骤 7 填入。 */
    private final Map<Long, String> finalTrialHashes = new HashMap<>();

    /** 取指定类型的步骤。 */
    public CloseStep step(CloseStepType type) {
        return steps.stream().filter(step -> step.getStepType() == type).findFirst()
            .orElseThrow(() -> new ErrorException("关账步骤缺失: " + type.getCode()));
    }

    /** 取指定账簿的证据。 */
    public PeriodCloseBook closeBook(long ledgerBookId) {
        return closeBooks.stream().filter(book -> book.getLedgerBookId() == ledgerBookId).findFirst()
            .orElseThrow(() -> new ErrorException("关账账簿证据缺失: " + ledgerBookId));
    }

    /** 全部账簿主键。 */
    public List<Long> bookIds() {
        return books.stream().map(LedgerBook::getId).toList();
    }
}
