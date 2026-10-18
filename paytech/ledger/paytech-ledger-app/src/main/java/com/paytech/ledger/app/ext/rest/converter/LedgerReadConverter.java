package com.paytech.ledger.app.ext.rest.converter;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.ledger.api.response.ChartAccountListResponseBody;
import com.paytech.ledger.api.response.ChartAccountResponseBody;
import com.paytech.ledger.api.response.LedgerAccountTotalResponseBody;
import com.paytech.ledger.api.response.LedgerAccountTotalsResponseBody;
import com.paytech.ledger.api.response.LedgerBookDetailResponseBody;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.domain.view.AccountView;
import com.paytech.ledger.domain.view.LedgerBookView;

/** 内部读取接口（/ledger/v1）的参数解析与 View 到 ResponseBody 的转换；ID 与金额输出为字符串。 */
@Component
public class LedgerReadConverter {

    /** 解析已由接口正则校验的逗号分隔主键。 */
    public List<Long> toIds(String ids) {
        return Arrays.stream(ids.split(",")).map(Long::valueOf).toList();
    }

    /** 转换账簿身份。 */
    public LedgerBookDetailResponseBody toResponseBody(LedgerBookView view) {
        LedgerBookDetailResponseBody body = new LedgerBookDetailResponseBody();
        body.setLedgerBookId(view.getLedgerBookId().toString());
        body.setEntityType(view.getEntityType().getCode());
        body.setEntityId(view.getEntityId());
        body.setCurrency(view.getCurrency());
        body.setChartId(view.getChartId().toString());
        body.setChartCode(view.getChartCode());
        body.setBookStatus(view.getBookStatus().getCode());
        return body;
    }

    /** 转换科目列表。 */
    public ChartAccountListResponseBody toResponseBody(List<ChartAccount> accounts) {
        ChartAccountListResponseBody body = new ChartAccountListResponseBody();
        body.setItems(accounts.stream().map(this::toChartBody).toList());
        return body;
    }

    /** 转换科目。 */
    ChartAccountResponseBody toChartBody(ChartAccount account) {
        ChartAccountResponseBody body = new ChartAccountResponseBody();
        body.setChartAccountId(account.getId().toString());
        body.setChartId(account.getChartId().toString());
        body.setAccountCode(account.getAccountCode());
        body.setAccountName(account.getAccountName());
        body.setAccountClass(account.getAccountClass().getCode());
        body.setNormalSide(account.getNormalSide().getCode());
        body.setPostingAllowed(account.getPostingAllowed());
        return body;
    }

    /** 转换账簿 Owner 账户的分录聚合余额。 */
    public LedgerAccountTotalsResponseBody toTotalsBody(LedgerBookView book, List<AccountView> accounts) {
        LedgerAccountTotalsResponseBody body = new LedgerAccountTotalsResponseBody();
        body.setLedgerBookId(book.getLedgerBookId().toString());
        body.setEntityType(book.getEntityType().getCode());
        body.setEntityId(book.getEntityId());
        body.setCurrency(book.getCurrency());
        body.setItems(accounts.stream().map(this::toTotalBody).toList());
        return body;
    }

    /** 转换一个账户的聚合余额。 */
    LedgerAccountTotalResponseBody toTotalBody(AccountView view) {
        LedgerAccountTotalResponseBody body = new LedgerAccountTotalResponseBody();
        body.setAccountId(view.getAccountId().toString());
        body.setAccountCode(view.getAccountCode());
        body.setOwnerType(view.getOwnerType().getCode());
        body.setOwnerId(view.getOwnerId());
        body.setBalanceBucket(view.getBalanceBucket().getCode());
        body.setNormalSide(view.getNormalSide().getCode());
        body.setDebitTotal(amount(view.getDebitTotal()));
        body.setCreditTotal(amount(view.getCreditTotal()));
        body.setNetBalance(amount(view.getNetBalance()));
        body.setLastJournalSequence(view.getLastJournalSequence().toString());
        return body;
    }

    /** 金额普通十进制字符串。 */
    String amount(BigDecimal value) {
        return value.toPlainString();
    }
}
