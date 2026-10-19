package com.paytech.ledger.app.ext.rest.converter;

import org.springframework.stereotype.Component;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.ledger.api.request.CreateAccountRequestBody;
import com.paytech.ledger.api.request.CreateAccountingPeriodRequestBody;
import com.paytech.ledger.api.request.CreateFinanceEntityRequestBody;
import com.paytech.ledger.api.request.CreateFiscalCalendarRequestBody;
import com.paytech.ledger.api.request.CreateLedgerBookRequestBody;
import com.paytech.ledger.api.response.OpenAccountingPeriodResponseBody;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.service.processor.request.CreateAccountRequest;
import com.paytech.ledger.service.processor.request.CreateAccountingPeriodRequest;
import com.paytech.ledger.service.processor.request.CreateFinanceEntityRequest;
import com.paytech.ledger.service.processor.request.CreateFiscalCalendarRequest;
import com.paytech.ledger.service.processor.request.CreateLedgerBookRequest;
import com.paytech.ledger.service.processor.response.OpenAccountingPeriodResponse;

/** 会计基础资料接口的 Wire 模型与内部请求/结果转换：字符串 ID 转 Long，Code 转内部枚举。 */
@Component
public class LedgerSetupConverter {

    /** 转换创建财务主体请求。 */
    public CreateFinanceEntityRequest toRequest(CreateFinanceEntityRequestBody body) {
        CreateFinanceEntityRequest request = new CreateFinanceEntityRequest();
        request.setEntityCode(body.getEntityCode());
        request.setLegalPartyId(Long.valueOf(body.getLegalPartyId()));
        request.setDisplayName(body.getDisplayName());
        return request;
    }

    /** 转换创建账簿请求。 */
    public CreateLedgerBookRequest toRequest(CreateLedgerBookRequestBody body) {
        CreateLedgerBookRequest request = new CreateLedgerBookRequest();
        request.setEntityType(CodeEnumConverter.parse(LedgerEntityType.class, body.getEntityType()));
        request.setEntityId(body.getEntityId());
        request.setCurrency(body.getCurrency());
        request.setChartCode(body.getChartCode());
        return request;
    }

    /** 转换创建账户请求。 */
    public CreateAccountRequest toRequest(String ledgerBookId, CreateAccountRequestBody body) {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setLedgerBookId(Long.valueOf(ledgerBookId));
        request.setAccountCode(body.getAccountCode());
        request.setOwnerType(CodeEnumConverter.parse(LedgerOwnerType.class, body.getOwnerType()));
        request.setOwnerId(body.getOwnerId());
        request.setBalanceBucket(CodeEnumConverter.parse(BalanceBucket.class, body.getBalanceBucket()));
        return request;
    }

    /** 转换创建财务日历请求。 */
    public CreateFiscalCalendarRequest toRequest(CreateFiscalCalendarRequestBody body) {
        CreateFiscalCalendarRequest request = new CreateFiscalCalendarRequest();
        request.setEntityType(CodeEnumConverter.parse(LedgerEntityType.class, body.getEntityType()));
        request.setEntityId(body.getEntityId());
        request.setCalendarCode(body.getCalendarCode());
        request.setTimezone(body.getTimezone());
        request.setYearStartMonth(body.getYearStartMonth());
        return request;
    }

    /** 转换创建会计期间请求。 */
    public CreateAccountingPeriodRequest toRequest(String fiscalCalendarId, CreateAccountingPeriodRequestBody body) {
        CreateAccountingPeriodRequest request = new CreateAccountingPeriodRequest();
        request.setFiscalCalendarId(Long.valueOf(fiscalCalendarId));
        request.setFiscalYear(body.getFiscalYear());
        request.setPeriodNo(body.getPeriodNo());
        request.setStartDate(body.getStartDate());
        request.setEndDate(body.getEndDate());
        return request;
    }

    /** 转换开放期间结果。 */
    public OpenAccountingPeriodResponseBody toResponseBody(OpenAccountingPeriodResponse response) {
        OpenAccountingPeriodResponseBody body = new OpenAccountingPeriodResponseBody();
        body.setAccountingPeriodId(response.getAccountingPeriodId().toString());
        body.setStatus(response.getStatus().getCode());
        return body;
    }
}
