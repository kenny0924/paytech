package com.paytech.reconciliation.app.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.reconciliation.api.clearing.ClearingAccountResolutionApi;
import com.paytech.reconciliation.api.clearing.request.ResolveClearingAccountRequestBody;
import com.paytech.reconciliation.api.clearing.response.ClearingAccountResolutionResponseBody;
import com.paytech.reconciliation.app.ext.rest.converter.ClearingAccountConverter;
import com.paytech.reconciliation.domainservice.service.ClearingAccountQueryService;

import lombok.AllArgsConstructor;

/** 清算账户解析（UC-R03，内部接口）的 HTTP 协议适配：简单本地查询，直接委托查询 Service。 */
@RestController
@AllArgsConstructor
public class ClearingAccountResolutionController implements ClearingAccountResolutionApi {
    private final ClearingAccountConverter converter;
    private final ClearingAccountQueryService clearingAccountQueryService;

    /** 按 MID + 币种解析。 */
    @Override
    public ClearingAccountResolutionResponseBody resolveClearingAccount(ResolveClearingAccountRequestBody body) {
        return converter.toResolutionResponseBody(
            clearingAccountQueryService.resolve(Long.parseLong(body.getMidId()), body.getCurrency()));
    }
}
