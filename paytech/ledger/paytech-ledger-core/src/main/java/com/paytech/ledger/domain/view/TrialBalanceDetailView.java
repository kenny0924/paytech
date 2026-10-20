package com.paytech.ledger.domain.view;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 试算详情：运行摘要、按科目编号排序的末级行与级次汇总行、异常。 */
@Getter
@AllArgsConstructor
public class TrialBalanceDetailView {
    /** 运行摘要。 */
    private final TrialBalanceRunView run;

    /** 试算行（末级科目，持久化于 t_trial_balance_line）。 */
    private final List<AccountBalanceView> lines;

    /** 查询时由科目树向上汇总的汇总行，不持久化、不进入内容摘要。 */
    private final List<AccountBalanceView> summaryLines;

    /** 异常。 */
    private final List<TrialBalanceFindingView> findings;
}
