package com.paytech.reconciliation.app.ext.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.reconciliation.api.clearing.ClearingAccountApi;
import com.paytech.reconciliation.api.clearing.request.AddClearingAccountMidRequestBody;
import com.paytech.reconciliation.api.clearing.request.ChangeClearingAccountStatusRequestBody;
import com.paytech.reconciliation.api.clearing.request.CreateClearingAccountRequestBody;
import com.paytech.reconciliation.api.clearing.request.UpdateClearingAccountSettingsRequestBody;
import com.paytech.reconciliation.api.clearing.response.ClearingAccountResponseBody;
import com.paytech.reconciliation.api.clearing.response.ClearingAccountSummaryResponseBody;
import com.paytech.reconciliation.api.clearing.response.UnassignedMidListResponseBody;
import com.paytech.reconciliation.api.enums.ClearingAccountStatus;
import com.paytech.reconciliation.app.ext.rest.converter.ClearingAccountConverter;
import com.paytech.reconciliation.app.ext.rest.converter.ReconciliationWire;
import com.paytech.reconciliation.domainservice.service.ClearingAccountQueryService;
import com.paytech.reconciliation.service.processor.AddClearingAccountMidProcessor;
import com.paytech.reconciliation.service.processor.ChangeClearingAccountStatusProcessor;
import com.paytech.reconciliation.service.processor.CreateClearingAccountProcessor;
import com.paytech.reconciliation.service.processor.ListUnassignedMidsProcessor;
import com.paytech.reconciliation.service.processor.RemoveClearingAccountMidProcessor;
import com.paytech.reconciliation.service.processor.UpdateClearingAccountSettingsProcessor;
import com.paytech.reconciliation.service.processor.request.ChangeClearingAccountStatusRequest;
import com.paytech.reconciliation.service.processor.request.ClearingAccountMidRequest;
import com.paytech.reconciliation.service.processor.request.ListUnassignedMidsRequest;

import lombok.AllArgsConstructor;

/** 清算账户维护与查询的 HTTP 协议适配：写入口委托 Processor，列表与详情委托查询 Service。 */
@RestController
@AllArgsConstructor
public class ClearingAccountController implements ClearingAccountApi {
    private final ClearingAccountConverter converter;
    private final ClearingAccountQueryService clearingAccountQueryService;
    private final ListUnassignedMidsProcessor listUnassignedMidsProcessor;
    private final CreateClearingAccountProcessor createClearingAccountProcessor;
    private final AddClearingAccountMidProcessor addClearingAccountMidProcessor;
    private final RemoveClearingAccountMidProcessor removeClearingAccountMidProcessor;
    private final ChangeClearingAccountStatusProcessor changeClearingAccountStatusProcessor;
    private final UpdateClearingAccountSettingsProcessor updateClearingAccountSettingsProcessor;

    /** 新建清算账户并返回 201。 */
    @Override
    public ResponseEntity<ClearingAccountResponseBody> createClearingAccount(String actorAccountId, String sessionId,
        CreateClearingAccountRequestBody body) {
        ClearingAccountResponseBody response = converter.toResponseBody(
            createClearingAccountProcessor.process(converter.toCreateRequest(body)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 修改配置。 */
    @Override
    public ClearingAccountResponseBody updateClearingAccountSettings(String actorAccountId, String sessionId,
        String clearingAccountId, UpdateClearingAccountSettingsRequestBody body) {
        return converter.toResponseBody(
            updateClearingAccountSettingsProcessor.process(converter.toSettingsRequest(clearingAccountId, body)));
    }

    /** 挂入 MID。 */
    @Override
    public ClearingAccountResponseBody addClearingAccountMid(String actorAccountId, String sessionId,
        String clearingAccountId, AddClearingAccountMidRequestBody body) {
        return converter.toResponseBody(addClearingAccountMidProcessor.process(new ClearingAccountMidRequest(
            ReconciliationWire.id(clearingAccountId), ReconciliationWire.id(body.getMidId()))));
    }

    /** 摘除 MID。 */
    @Override
    public ClearingAccountResponseBody removeClearingAccountMid(String actorAccountId, String sessionId,
        String clearingAccountId, String midId) {
        return converter.toResponseBody(removeClearingAccountMidProcessor.process(new ClearingAccountMidRequest(
            ReconciliationWire.id(clearingAccountId), ReconciliationWire.id(midId))));
    }

    /** 启用或停用。 */
    @Override
    public ClearingAccountResponseBody changeClearingAccountStatus(String actorAccountId, String sessionId,
        String clearingAccountId, ChangeClearingAccountStatusRequestBody body) {
        return converter.toResponseBody(changeClearingAccountStatusProcessor.process(
            new ChangeClearingAccountStatusRequest(ReconciliationWire.id(clearingAccountId),
                ReconciliationWire.parse(ClearingAccountStatus.class, body.getStatus()))));
    }

    /** 分页查询。 */
    @Override
    public PagingResponseBody<ClearingAccountSummaryResponseBody> listClearingAccounts(String entityNo,
        String channelCode, String currency, String status, Integer page, Integer size) {
        return clearingAccountQueryService.listClearingAccounts(entityNo, channelCode, currency,
            ReconciliationWire.parse(ClearingAccountStatus.class, status), PagingRequestBody.of(page, size))
            .map(converter::toSummaryResponseBody);
    }

    /** 详情。 */
    @Override
    public ClearingAccountResponseBody getClearingAccount(String clearingAccountId) {
        return converter.toResponseBody(clearingAccountQueryService.getClearingAccount(Long.parseLong(clearingAccountId)));
    }

    /** 未归属 MID。 */
    @Override
    public UnassignedMidListResponseBody listUnassignedMids(String entityNo, String channelCode, String currency) {
        return converter.toUnassignedResponseBody(listUnassignedMidsProcessor.process(
            new ListUnassignedMidsRequest(entityNo, channelCode, currency)));
    }
}
