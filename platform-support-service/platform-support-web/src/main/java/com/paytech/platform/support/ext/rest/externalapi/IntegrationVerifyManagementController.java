package com.paytech.platform.support.ext.rest.externalapi;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.externalapi.IntegrationVerifyManagementApi;
import com.paytech.platform.support.api.externalapi.request.CreateVerifyAccountRequestBody;
import com.paytech.platform.support.api.externalapi.request.CreateVerifyCaseRequestBody;
import com.paytech.platform.support.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.platform.support.api.externalapi.request.StartVerifyRunRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateVerifyAccountRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateVerifyCaseRequestBody;
import com.paytech.platform.support.api.externalapi.request.ValidateVerifyCaseRequestBody;
import com.paytech.platform.support.api.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.platform.support.api.externalapi.response.ValidationResponseBody;
import com.paytech.platform.support.api.externalapi.response.VerifyAccountResponseBody;
import com.paytech.platform.support.api.externalapi.response.VerifyAccountSummary;
import com.paytech.platform.support.api.externalapi.response.VerifyCaseResponseBody;
import com.paytech.platform.support.api.externalapi.response.VerifyCaseSummary;
import com.paytech.platform.support.api.externalapi.response.VerifyRunResponseBody;
import com.paytech.platform.support.api.externalapi.response.VerifyRunStartResponseBody;
import com.paytech.platform.support.api.externalapi.response.VerifyRunSummary;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.integration.service.processor.*;

import lombok.AllArgsConstructor;

/** 渠道沙箱验证管理的协议入口，规则和事务委托 integration 模块的 Processor。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
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
    @Audit(action = "OPERATIONS_VERIFY_ACCOUNTS", resourceType = "INTEGRATION_VERIFY_ACCOUNT",
        fallbackResourceId = "VERIFY_ACCOUNT_CATALOG", recordSuccess = false)
    @Override
    public List<VerifyAccountSummary> listVerifyAccounts(String actor, String session, String integrationCode) {
        return converter.toAccounts(listAccounts.process(
            common.toChildListRequest(actor, session, integrationCode, null, null, null, null)));
    }

    /** V02 委托账号详情用例。 */
    @Audit(action = "OPERATIONS_VERIFY_ACCOUNT", resourceType = "INTEGRATION_VERIFY_ACCOUNT",
        fallbackResourceId = "VERIFY_ACCOUNT_CATALOG", recordSuccess = false)
    @Override
    public VerifyAccountResponseBody getVerifyAccount(String actor, String session, String integrationCode,
        String accountCode) {
        return converter.toAccount(getAccount.process(
            common.toChildGetRequest(actor, session, integrationCode, accountCode)));
    }

    /** V03 委托账号创建用例。 */
    @Audit(action = "OPERATIONS_VERIFY_ACCOUNT_CREATE", resourceType = "INTEGRATION_VERIFY_ACCOUNT",
        fallbackResourceId = "NEW_VERIFY_ACCOUNT")
    @Override
    public ExternalApiMutationResponseBody createVerifyAccount(String actor, String session, String integrationCode,
        CreateVerifyAccountRequestBody request) {
        return common.toMutation(createAccount.process(
            converter.toCreateAccount(actor, session, integrationCode, request)));
    }

    /** V04 委托账号修改用例。 */
    @Audit(action = "OPERATIONS_VERIFY_ACCOUNT_UPDATE", resourceType = "INTEGRATION_VERIFY_ACCOUNT",
        fallbackResourceId = "VERIFY_ACCOUNT_CATALOG")
    @Override
    public ExternalApiMutationResponseBody updateVerifyAccount(String actor, String session, String integrationCode,
        String accountCode, UpdateVerifyAccountRequestBody request) {
        return common.toMutation(updateAccount.process(
            converter.toUpdateAccount(actor, session, integrationCode, accountCode, request)));
    }

    /** V05 委托账号删除用例。 */
    @Audit(action = "OPERATIONS_VERIFY_ACCOUNT_DELETE", resourceType = "INTEGRATION_VERIFY_ACCOUNT",
        fallbackResourceId = "VERIFY_ACCOUNT_CATALOG")
    @Override
    public ExternalApiMutationResponseBody deleteVerifyAccount(String actor, String session, String integrationCode,
        String accountCode, DeleteExternalApiConfigRequestBody request) {
        return common.toMutation(deleteAccount.process(
            common.toChildDeleteRequest(actor, session, integrationCode, accountCode, request)));
    }

    /** V06 委托用例列表用例。 */
    @Audit(action = "OPERATIONS_VERIFY_CASES", resourceType = "INTEGRATION_VERIFY_CASE",
        fallbackResourceId = "VERIFY_CASE_CATALOG", recordSuccess = false)
    @Override
    public PagingResponseBody<VerifyCaseSummary> listVerifyCases(String actor, String session, String integrationCode,
        Integer page, Integer size, String keyword) {
        return converter.toCasePage(listCases.process(
            common.toChildListRequest(actor, session, integrationCode, page, size, keyword, null)));
    }

    /** V07 委托用例详情用例。 */
    @Audit(action = "OPERATIONS_VERIFY_CASE", resourceType = "INTEGRATION_VERIFY_CASE",
        fallbackResourceId = "VERIFY_CASE_CATALOG", recordSuccess = false)
    @Override
    public VerifyCaseResponseBody getVerifyCase(String actor, String session, String integrationCode, String caseCode) {
        return converter.toCase(getCase.process(common.toChildGetRequest(actor, session, integrationCode, caseCode)));
    }

    /** V08 委托用例创建用例。 */
    @Audit(action = "OPERATIONS_VERIFY_CASE_CREATE", resourceType = "INTEGRATION_VERIFY_CASE",
        fallbackResourceId = "NEW_VERIFY_CASE")
    @Override
    public ExternalApiMutationResponseBody createVerifyCase(String actor, String session, String integrationCode,
        CreateVerifyCaseRequestBody request) {
        return common.toMutation(createCase.process(converter.toCreateCase(actor, session, integrationCode, request)));
    }

    /** V09 委托用例修改用例。 */
    @Audit(action = "OPERATIONS_VERIFY_CASE_UPDATE", resourceType = "INTEGRATION_VERIFY_CASE",
        fallbackResourceId = "VERIFY_CASE_CATALOG")
    @Override
    public ExternalApiMutationResponseBody updateVerifyCase(String actor, String session, String integrationCode,
        String caseCode, UpdateVerifyCaseRequestBody request) {
        return common.toMutation(updateCase.process(
            converter.toUpdateCase(actor, session, integrationCode, caseCode, request)));
    }

    /** V10 委托用例删除用例。 */
    @Audit(action = "OPERATIONS_VERIFY_CASE_DELETE", resourceType = "INTEGRATION_VERIFY_CASE",
        fallbackResourceId = "VERIFY_CASE_CATALOG")
    @Override
    public ExternalApiMutationResponseBody deleteVerifyCase(String actor, String session, String integrationCode,
        String caseCode, DeleteExternalApiConfigRequestBody request) {
        return common.toMutation(deleteCase.process(
            common.toChildDeleteRequest(actor, session, integrationCode, caseCode, request)));
    }

    /** V11 委托用例校验用例。 */
    @Audit(action = "OPERATIONS_VERIFY_CASE_VALIDATE", resourceType = "INTEGRATION_VERIFY_CASE",
        fallbackResourceId = "VERIFY_CASE_CATALOG", recordSuccess = false)
    @Override
    public ValidationResponseBody validateVerifyCase(String actor, String session, String integrationCode,
        ValidateVerifyCaseRequestBody request) {
        return common.toValidation(validateCase.process(
            converter.toValidateCase(actor, session, integrationCode, request)));
    }

    /** V12 委托发起运行用例。 */
    @Audit(action = "OPERATIONS_VERIFY_RUN_START", resourceType = "INTEGRATION_VERIFY_RUN",
        fallbackResourceId = "NEW_VERIFY_RUN")
    @Override
    public VerifyRunStartResponseBody startVerifyRun(String actor, String session, String integrationCode,
        String caseCode, StartVerifyRunRequestBody request) {
        return converter.toRunStart(startRun.process(
            converter.toRunStart(actor, session, integrationCode, caseCode, request)));
    }

    /** V13 委托运行列表用例。 */
    @Audit(action = "OPERATIONS_VERIFY_RUNS", resourceType = "INTEGRATION_VERIFY_RUN",
        fallbackResourceId = "VERIFY_RUN_CATALOG", recordSuccess = false)
    @Override
    public PagingResponseBody<VerifyRunSummary> listVerifyRuns(String actor, String session, String integrationCode,
        String caseCode, Integer page, Integer size) {
        return converter.toRunPage(listRuns.process(
            converter.toRunList(actor, session, integrationCode, caseCode, page, size)));
    }

    /** V14 委托运行详情用例。 */
    @Audit(action = "OPERATIONS_VERIFY_RUN", resourceType = "INTEGRATION_VERIFY_RUN",
        fallbackResourceId = "VERIFY_RUN_CATALOG", recordSuccess = false)
    @Override
    public VerifyRunResponseBody getVerifyRun(String actor, String session, String integrationCode, String runCode) {
        return converter.toRun(getRun.process(converter.toRunGet(actor, session, integrationCode, runCode)));
    }
}
