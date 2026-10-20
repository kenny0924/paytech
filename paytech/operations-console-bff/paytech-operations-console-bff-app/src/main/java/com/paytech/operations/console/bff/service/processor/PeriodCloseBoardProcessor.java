package com.paytech.operations.console.bff.service.processor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.FinancePeriodCloseClient;
import com.paytech.operations.console.bff.integration.FinanceSetupClient;
import com.paytech.operations.console.bff.service.model.FinancePeriodCloseModels.PeriodCloseBoardItemResponseBody;
import com.paytech.operations.console.bff.service.model.FinancePeriodCloseModels.PeriodCloseBoardResponseBody;
import com.paytech.operations.console.bff.service.model.FinancePeriodCloseModels.PeriodCloseResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.AccountingPeriodResponseBody;
import com.paytech.operations.console.bff.service.processor.request.PeriodCloseBoardRequest;

import lombok.AllArgsConstructor;

/**
 * 期间关账看板聚合（F1）：先分页读完日历下全部会计期间，再逐期读取关账/重开流程第 1 页，取序号最大的一条作为最近流程
 * <li>期间按 ledger 返回的财年、期号升序；从未发起流程的期间 latestClose 为空。</li>
 * <li>只读组合，不开事务、不编排写入；任一读取失败整页失败，不伪造空流程。</li>
 * <li>期间数受日历限制（通常不超过 24 个），逐期顺序读取。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class PeriodCloseBoardProcessor extends AbstractProcessor<PeriodCloseBoardRequest, PeriodCloseBoardResponseBody> {
    /** 读取会计期间的每页条数（ledger 允许的最大值）。 */
    static final int PERIOD_PAGE_SIZE = 200;

    /** 读取关账流程的每页条数；只取第 1 页的第一条。 */
    static final int CLOSE_PAGE_SIZE = 10;

    private final FinanceSetupClient setupClient;
    private final FinancePeriodCloseClient periodCloseClient;

    @Override
    protected PeriodCloseBoardResponseBody processRequest(PeriodCloseBoardRequest request) {
        var board = new PeriodCloseBoardResponseBody();
        board.setFiscalCalendarId(request.getFiscalCalendarId());
        board.setPeriods(loadPeriods(request.getFiscalCalendarId()).stream().map(this::boardItem).toList());
        return board;
    }

    /** 分页读完日历下的全部会计期间。 */
    private List<AccountingPeriodResponseBody> loadPeriods(String fiscalCalendarId) {
        var periods = new ArrayList<AccountingPeriodResponseBody>();
        for (int page = 1; ; page++) {
            var current = setupClient.listAccountingPeriods(fiscalCalendarId, null, page, PERIOD_PAGE_SIZE);
            periods.addAll(current.items());
            if ((long) page * PERIOD_PAGE_SIZE >= current.total()) {
                return periods;
            }
        }
    }

    /** 期间及其最近一次流程。 */
    private PeriodCloseBoardItemResponseBody boardItem(AccountingPeriodResponseBody period) {
        List<PeriodCloseResponseBody> closes = periodCloseClient.listPeriodCloses(period.getAccountingPeriodId(), 1,
            CLOSE_PAGE_SIZE).items();
        var item = new PeriodCloseBoardItemResponseBody();
        item.setPeriod(period);
        item.setLatestClose(closes.isEmpty() ? null : closes.getFirst());
        return item;
    }
}
