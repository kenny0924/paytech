package com.paytech.ledger.app.ext.rest.converter;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.ledger.api.response.ChartAccountListResponseBody;
import com.paytech.ledger.api.response.ChartAccountResponseBody;
import com.paytech.ledger.api.response.LedgerBookDetailResponseBody;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.domain.view.LedgerBookView;
import com.paytech.ledger.domainservice.validator.chart.ChartAccountNoRule;

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

    /** 转换科目；级次由科目编号长度推出。 */
    public ChartAccountResponseBody toChartBody(ChartAccount account) {
        ChartAccountResponseBody body = new ChartAccountResponseBody();
        body.setChartAccountId(account.getId().toString());
        body.setChartId(account.getChartId().toString());
        body.setAccountNo(account.getAccountNo());
        body.setAccountCode(account.getAccountCode());
        body.setAccountName(account.getAccountName());
        body.setAccountClass(account.getAccountClass().getCode());
        body.setNormalSide(account.getNormalSide().getCode());
        body.setPostingAllowed(account.getPostingAllowed());
        body.setParentChartAccountId(account.getParentAccountId() == null ? null
            : account.getParentAccountId().toString());
        body.setLevel(ChartAccountNoRule.level(account.getAccountNo()));
        body.setAccountStatus(account.getAccountStatus().getCode());
        return body;
    }

    /** 金额普通十进制字符串。 */
    String amount(BigDecimal value) {
        return value.toPlainString();
    }
}
