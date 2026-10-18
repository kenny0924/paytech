package com.paytech.account.app.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.account.api.BalanceBookApi;
import com.paytech.account.api.request.RebuildBookBalancesRequestBody;
import com.paytech.account.api.response.BalanceConsistencyResponseBody;
import com.paytech.account.api.response.BalanceRebuildResponseBody;
import com.paytech.account.api.response.BookBalancesResponseBody;
import com.paytech.account.app.ext.rest.converter.BalanceBookConverter;
import com.paytech.account.service.processor.GetBalanceConsistencyProcessor;
import com.paytech.account.service.processor.ListBookBalancesProcessor;
import com.paytech.account.service.processor.RebuildBookBalancesProcessor;
import com.paytech.account.service.processor.request.LedgerBookRequest;
import com.paytech.account.service.processor.request.RebuildBookBalancesRequest;

import lombok.AllArgsConstructor;

/** 账簿维度的余额一致性、列表与重建（design/04 §8）的 HTTP 协议适配；每个入口委托一个 Processor。 */
@RestController
@AllArgsConstructor
public class BalanceBookController implements BalanceBookApi {
    private final BalanceBookConverter converter;
    private final ListBookBalancesProcessor listProcessor;
    private final RebuildBookBalancesProcessor rebuildProcessor;
    private final GetBalanceConsistencyProcessor consistencyProcessor;

    /** 账簿的余额消费进度。 */
    @Override
    public BalanceConsistencyResponseBody getBalanceConsistency(String bookId) {
        return converter.toResponseBody(consistencyProcessor.process(new LedgerBookRequest(Long.parseLong(bookId))));
    }

    /** 账簿内全部余额账户。 */
    @Override
    public BookBalancesResponseBody listBookBalances(String bookId) {
        return converter.toResponseBody(listProcessor.process(new LedgerBookRequest(Long.parseLong(bookId))));
    }

    /** 从 billing 重放重算账簿余额。 */
    @Override
    public BalanceRebuildResponseBody rebuildBookBalances(String bookId, RebuildBookBalancesRequestBody body) {
        return converter.toResponseBody(rebuildProcessor.process(
            new RebuildBookBalancesRequest(Long.parseLong(bookId), Long.parseLong(body.getOperatorId()))));
    }
}
