package com.paytech.billing.test.mocker.external;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.ledger.api.response.FinanceEntityResponseBody;

/** ledger 按法律 Party 解析 Finance Entity 的 Mock：按 Party 主键配置响应或故障。 */
@TestComponent
public class FinanceEntityMocker extends DtoResponseMocker<String, FinanceEntityResponseBody> {

    /** 上游名 ledger。 */
    public FinanceEntityMocker() {
        super("ledger");
    }

    /** 由 Mock Controller 调用。 */
    public FinanceEntityResponseBody getByParty(String legalPartyId) {
        return answer("getFinanceEntityByParty", legalPartyId);
    }
}
