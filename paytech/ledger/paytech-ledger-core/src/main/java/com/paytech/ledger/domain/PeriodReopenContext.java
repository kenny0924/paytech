package com.paytech.ledger.domain;

import java.util.List;
import java.util.Map;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.ledger.core.dal.dataobject.AccountingPeriod;
import com.paytech.ledger.core.dal.dataobject.CloseStep;
import com.paytech.ledger.core.dal.dataobject.InstructionLine;
import com.paytech.ledger.core.dal.dataobject.PeriodClose;
import com.paytech.ledger.core.dal.dataobject.PeriodCloseBook;
import com.paytech.ledger.core.enums.CloseStepType;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 一次期间重开：期间、原关账、重开流程根、账簿证据、3 个步骤与按账簿的冲回计划。 */
@Getter
@RequiredArgsConstructor
public class PeriodReopenContext {
    /** 被重开的期间；状态迁移成功后由 Repository 同步。 */
    private final AccountingPeriod period;

    /** 被引用的原关账流程（CLOSE 且 COMPLETED）。 */
    private final PeriodClose reopensClose;

    /** 重开流程根。 */
    private final PeriodClose close;

    /** 账簿证据，与原关账的账簿一一对应，按账簿主键升序。 */
    private final List<PeriodCloseBook> closeBooks;

    /** 3 个固定步骤，按步骤号升序。 */
    private final List<CloseStep> steps;

    /** 账簿主键 → 冲回行（原结转指令逐行反向）；原关账无结转指令的账簿为空列表。按账簿主键升序。 */
    private final Map<Long, List<InstructionLine>> reversalLines;

    /** 取指定类型的步骤。 */
    public CloseStep step(CloseStepType type) {
        return steps.stream().filter(step -> step.getStepType() == type).findFirst()
            .orElseThrow(() -> new ErrorException("重开步骤缺失: " + type.getCode()));
    }
}
