package com.paytech.channel.connector.ext.rest.externalapi;

import java.util.List;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.channel.connector.api.externalapi.IntegrationVerifyManagementApi;
import com.paytech.channel.connector.api.externalapi.request.CreateVerifyAccountRequestBody;
import com.paytech.channel.connector.api.externalapi.request.CreateVerifyCaseRequestBody;
import com.paytech.channel.connector.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.channel.connector.api.externalapi.request.StartVerifyRunRequestBody;
import com.paytech.channel.connector.api.externalapi.request.UpdateVerifyAccountRequestBody;
import com.paytech.channel.connector.api.externalapi.request.UpdateVerifyCaseRequestBody;
import com.paytech.channel.connector.api.externalapi.request.ValidateVerifyCaseRequestBody;
import com.paytech.channel.connector.api.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.channel.connector.api.externalapi.response.ValidationResponseBody;
import com.paytech.channel.connector.api.externalapi.response.VerifyAccountResponseBody;
import com.paytech.channel.connector.api.externalapi.response.VerifyAccountSummary;
import com.paytech.channel.connector.api.externalapi.response.VerifyCaseResponseBody;
import com.paytech.channel.connector.api.externalapi.response.VerifyCaseSummary;
import com.paytech.channel.connector.api.externalapi.response.VerifyRunResponseBody;
import com.paytech.channel.connector.api.externalapi.response.VerifyRunStartResponseBody;
import com.paytech.channel.connector.api.externalapi.response.VerifyRunSummary;
import com.paytech.channel.connector.protocol.service.processor.*;

import lombok.AllArgsConstructor;

/** 渠道沙箱验证管理的协议入口，规则和事务委托 protocol 模块的 Processor。 */
@RestController
@AllArgsConstructor
public class IntegrationVerifyManagementController implements IntegrationVerifyManagementApi {

    private final ExternalApiManagementConverter common;
    private final VerifyManagementConverter converter;
    private final ListVerifyAccountsProcessor listAccounts;
    private final GetVerifyAccountProcessor getAccount;
    private final CreateVerifyAccountProcessor createAccount;
    private final UpdateVerifyAccountProcessor updateAccount;
    private final DeleteVerifyAccountProcessor deleteAccount;
    private final ListVerifyCasesProcessor listCases;
    private final GetVerifyCaseProcessor getCase;
    private final CreateVerifyCaseProcessor createCase;
    private final UpdateVerifyCaseProcessor updateCase;
    private final DeleteVerifyCaseProcessor deleteCase;
    private final ValidateVerifyCaseProcessor validateCase;
    private final StartVerifyRunProcessor startRun;
    private final ListVerifyRunsProcessor listRuns;
    private final GetVerifyRunProcessor getRun;

    /** V01 委托账号列表用例。 */
    @Override
    public List<VerifyAccountSummary> listVerifyAccounts(String integrationCode) {
        return converter.toAccounts(listAccounts.process(
            common.toChildListRequest(integrationCode, null, null, null, null)));
    }

    /** V02 委托账号详情用例。 */
    @Override
    public VerifyAccountResponseBody getVerifyAccount(String integrationCode,
        String accountCode) {
        return converter.toAccount(getAccount.process(
            common.toChildGetRequest(integrationCode, accountCode)));
    }

    /** V03 委托账号创建用例。 */
    @Override
    public ExternalApiMutationResponseBody createVerifyAccount(String actor, String integrationCode,
        CreateVerifyAccountRequestBody request) {
        return common.toMutation(createAccount.process(
            converter.toCreateAccount(actor, integrationCode, request)));
    }

    /** V04 委托账号修改用例。 */
    @Override
    public ExternalApiMutationResponseBody updateVerifyAccount(String actor, String integrationCode,
        String accountCode, UpdateVerifyAccountRequestBody request) {
        return common.toMutation(updateAccount.process(
            converter.toUpdateAccount(actor, integrationCode, accountCode, request)));
    }

    /** V05 委托账号删除用例。 */
    @Override
    public ExternalApiMutationResponseBody deleteVerifyAccount(String actor, String integrationCode,
        String accountCode, DeleteExternalApiConfigRequestBody request) {
        return common.toMutation(deleteAccount.process(
            common.toChildDeleteRequest(actor, integrationCode, accountCode, request)));
    }

    /** V06 委托用例列表用例。 */
    @Override
    public PagingResponseBody<VerifyCaseSummary> listVerifyCases(String integrationCode,
        Integer page, Integer size, String keyword) {
        return converter.toCasePage(listCases.process(
            common.toChildListRequest(integrationCode, page, size, keyword, null)));
    }

    /** V07 委托用例详情用例。 */
    @Override
    public VerifyCaseResponseBody getVerifyCase(String integrationCode, String caseCode) {
        return converter.toCase(getCase.process(common.toChildGetRequest(integrationCode, caseCode)));
    }

    /** V08 委托用例创建用例。 */
    @Override
    public ExternalApiMutationResponseBody createVerifyCase(String actor, String integrationCode,
        CreateVerifyCaseRequestBody request) {
        return common.toMutation(createCase.process(converter.toCreateCase(actor, integrationCode, request)));
    }

    /** V09 委托用例修改用例。 */
    @Override
    public ExternalApiMutationResponseBody updateVerifyCase(String actor, String integrationCode,
        String caseCode, UpdateVerifyCaseRequestBody request) {
        return common.toMutation(updateCase.process(
            converter.toUpdateCase(actor, integrationCode, caseCode, request)));
    }

    /** V10 委托用例删除用例。 */
    @Override
    public ExternalApiMutationResponseBody deleteVerifyCase(String actor, String integrationCode,
        String caseCode, DeleteExternalApiConfigRequestBody request) {
        return common.toMutation(deleteCase.process(
            common.toChildDeleteRequest(actor, integrationCode, caseCode, request)));
    }

    /** V11 委托用例校验用例。 */
    @Override
    public ValidationResponseBody validateVerifyCase(String integrationCode,
        ValidateVerifyCaseRequestBody request) {
        return common.toValidation(validateCase.process(
            converter.toValidateCase(integrationCode, request)));
    }

    /** V12 委托发起运行用例。 */
    @Override
    public VerifyRunStartResponseBody startVerifyRun(String actor, String integrationCode,
        String caseCode, StartVerifyRunRequestBody request) {
        return converter.toRunStart(startRun.process(
            converter.toRunStart(actor, integrationCode, caseCode, request)));
    }

    /** V13 委托运行列表用例。 */
    @Override
    public PagingResponseBody<VerifyRunSummary> listVerifyRuns(String integrationCode,
        String caseCode, Integer page, Integer size) {
        return converter.toRunPage(listRuns.process(
            converter.toRunList(integrationCode, caseCode, page, size)));
    }

    /** V14 委托运行详情用例。 */
    @Override
    public VerifyRunResponseBody getVerifyRun(String integrationCode, String runCode) {
        return converter.toRun(getRun.process(converter.toRunGet(integrationCode, runCode)));
    }
}
