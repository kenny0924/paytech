package com.paytech.ledger.app.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.ledger.api.LedgerReadApi;
import com.paytech.ledger.api.response.ChartAccountListResponseBody;
import com.paytech.ledger.api.response.LedgerAccountTotalsResponseBody;
import com.paytech.ledger.api.response.LedgerBookDetailResponseBody;
import com.paytech.ledger.app.ext.rest.converter.LedgerReadConverter;
import com.paytech.ledger.domain.view.LedgerBookView;
import com.paytech.ledger.domainservice.service.LedgerQueryService;

import lombok.AllArgsConstructor;

/** 内部读取接口（design/04 §8）的 HTTP 协议适配；简单只读查询直接委托 LedgerQueryService。 */
@RestController
@AllArgsConstructor
public class LedgerReadController implements LedgerReadApi {
    private final LedgerQueryService ledgerQueryService;
    private final LedgerReadConverter converter;

    /** 查询账簿身份。 */
    @Override
    public LedgerBookDetailResponseBody getLedgerBook(String bookId) {
        return converter.toResponseBody(ledgerQueryService.getLedgerBook(Long.parseLong(bookId)));
    }

    /** 批量读取科目。 */
    @Override
    public ChartAccountListResponseBody listChartAccounts(String ids) {
        return converter.toResponseBody(ledgerQueryService.listChartAccounts(converter.toIds(ids)));
    }

    /** 读取 Owner 账户按分录聚合的余额。 */
    @Override
    public LedgerAccountTotalsResponseBody getAccountTotals(String bookId) {
        LedgerBookView book = ledgerQueryService.getLedgerBook(Long.parseLong(bookId));
        return converter.toTotalsBody(book, ledgerQueryService.listOwnerAccountTotals(book.getLedgerBookId()));
    }
}
