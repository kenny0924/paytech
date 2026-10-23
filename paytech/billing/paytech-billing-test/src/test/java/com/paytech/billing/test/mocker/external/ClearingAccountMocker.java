package com.paytech.billing.test.mocker.external;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.reconciliation.api.clearing.response.ClearingAccountResolutionResponseBody;

/** reconciliation 清算账户解析 Mock：按 MID 主键配置响应或故障。 */
@TestComponent
public class ClearingAccountMocker extends DtoResponseMocker<String, ClearingAccountResolutionResponseBody> {

    /** 上游名 reconciliation。 */
    public ClearingAccountMocker() {
        super("reconciliation");
    }

    /** 由 Mock Controller 调用。 */
    public ClearingAccountResolutionResponseBody resolve(String midId) {
        return answer("resolveClearingAccount", midId);
    }
}
