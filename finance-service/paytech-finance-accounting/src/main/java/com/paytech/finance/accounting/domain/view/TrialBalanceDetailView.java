package com.paytech.finance.accounting.domain.view;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 试算详情：运行摘要、按科目代码排序的行与异常。 */
@Getter
@AllArgsConstructor
public class TrialBalanceDetailView {
    /** 运行摘要。 */
    private final TrialBalanceRunView run;

    /** 试算行。 */
    private final List<TrialBalanceLineView> lines;

    /** 异常。 */
    private final List<TrialBalanceFindingView> findings;
}
