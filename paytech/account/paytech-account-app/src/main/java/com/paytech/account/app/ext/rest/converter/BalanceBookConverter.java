package com.paytech.account.app.ext.rest.converter;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.api.response.BalanceConsistencyResponseBody;
import com.paytech.account.api.response.BalanceRebuildResponseBody;
import com.paytech.account.api.response.BookBalanceResponseBody;
import com.paytech.account.api.response.BookBalancesResponseBody;
import com.paytech.account.api.response.OwnerBalanceCountResponseBody;
import com.paytech.account.core.dal.dataobject.AccountBalance;
import com.paytech.account.domain.LedgerBookRef;
import com.paytech.account.domain.view.BalanceRebuildView;
import com.paytech.account.domain.view.BookConsistencyView;
import com.paytech.account.service.processor.ListBookBalancesProcessor;
import com.paytech.foundation.money.Money;

/** 账簿余额 View 到 ResponseBody 的转换；ID、计数与金额输出为字符串，金额按币种精度补齐。 */
@Component
public class BalanceBookConverter {

    /** 转换消费进度。 */
    public BalanceConsistencyResponseBody toResponseBody(BookConsistencyView view) {
        BalanceConsistencyResponseBody body = new BalanceConsistencyResponseBody();
        fill(view.book(), body);
        body.setAppliedFactCount(Long.toString(view.appliedFactCount()));
        body.setMaxAppliedFactId(view.maxAppliedFactId() == null ? null : view.maxAppliedFactId().toString());
        body.setNonZeroAccountCounts(view.nonZeroAccounts().entrySet().stream().map(this::count).toList());
        return body;
    }

    /** 转换账簿余额列表。 */
    public BookBalancesResponseBody toResponseBody(ListBookBalancesProcessor.Result result) {
        BookBalancesResponseBody body = new BookBalancesResponseBody();
        LedgerBookRef book = result.book();
        body.setLedgerBookId(Long.toString(book.ledgerBookId()));
        body.setBookEntityType(book.identity().entityType().getCode());
        body.setBookEntityId(book.identity().entityId());
        body.setCurrency(book.identity().currency());
        body.setItems(result.balances().stream().map(balance -> toBalanceBody(book.identity().currency(), balance))
            .toList());
        return body;
    }

    /** 转换重建结果。 */
    public BalanceRebuildResponseBody toResponseBody(BalanceRebuildView view) {
        BalanceRebuildResponseBody body = new BalanceRebuildResponseBody();
        body.setLedgerBookId(Long.toString(view.book().ledgerBookId()));
        body.setReplayedFactCount(Integer.toString(view.replayedFactCount()));
        body.setMaxFactId(view.maxFactId() == null ? null : view.maxFactId().toString());
        body.setBalanceCount(Integer.toString(view.balanceCount()));
        return body;
    }

    /** 账簿身份字段。 */
    void fill(LedgerBookRef book, BalanceConsistencyResponseBody body) {
        body.setLedgerBookId(Long.toString(book.ledgerBookId()));
        body.setBookEntityType(book.identity().entityType().getCode());
        body.setBookEntityId(book.identity().entityId());
        body.setCurrency(book.identity().currency());
    }

    /** 一个 Owner 类型的非零计数。 */
    OwnerBalanceCountResponseBody count(Map.Entry<OwnerType, Long> entry) {
        OwnerBalanceCountResponseBody body = new OwnerBalanceCountResponseBody();
        body.setOwnerType(entry.getKey().getCode());
        body.setCount(entry.getValue().toString());
        return body;
    }

    /** 一个余额账户。 */
    BookBalanceResponseBody toBalanceBody(String currency, AccountBalance balance) {
        BookBalanceResponseBody body = new BookBalanceResponseBody();
        body.setBalanceId(balance.getId().toString());
        body.setAccountCode(balance.getAccountCode());
        body.setOwnerType(balance.getOwnerType().getCode());
        body.setOwnerId(balance.getOwnerId());
        body.setBalanceBucket(balance.getBalanceBucket().getCode());
        body.setNormalSide(balance.getNormalSide().getCode());
        body.setDebitTotal(amount(currency, balance.getDebitTotal()));
        body.setCreditTotal(amount(currency, balance.getCreditTotal()));
        body.setNetBalance(amount(currency, balance.getNetBalance()));
        body.setLastFactId(balance.getLastFactId() == null ? null : balance.getLastFactId().toString());
        body.setLastFactVersion(balance.getLastFactVersion() == null ? null : balance.getLastFactVersion().toString());
        return body;
    }

    /** 金额按币种精度补齐的普通十进制字符串。 */
    String amount(String currency, BigDecimal value) {
        return Money.of(currency, value).toAmountString();
    }
}
