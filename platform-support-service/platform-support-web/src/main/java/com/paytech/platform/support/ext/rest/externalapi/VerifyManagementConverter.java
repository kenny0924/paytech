package com.paytech.platform.support.ext.rest.externalapi;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementInputs;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationInputs;
import com.paytech.platform.support.api.externalapi.request.CreateVerifyAccountRequestBody;
import com.paytech.platform.support.api.externalapi.request.CreateVerifyCaseRequestBody;
import com.paytech.platform.support.api.externalapi.request.StartVerifyRunRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateVerifyAccountRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateVerifyCaseRequestBody;
import com.paytech.platform.support.api.externalapi.request.ValidateVerifyCaseRequestBody;
import com.paytech.platform.support.api.externalapi.response.VerifyAccountResponseBody;
import com.paytech.platform.support.api.externalapi.response.VerifyAccountSummary;
import com.paytech.platform.support.api.externalapi.response.VerifyCaseResponseBody;
import com.paytech.platform.support.api.externalapi.response.VerifyCaseSummary;
import com.paytech.platform.support.api.externalapi.response.VerifyRunBrief;
import com.paytech.platform.support.api.externalapi.response.VerifyRunResponseBody;
import com.paytech.platform.support.api.externalapi.response.VerifyRunStartResponseBody;
import com.paytech.platform.support.api.externalapi.response.VerifyRunStepDetail;
import com.paytech.platform.support.api.externalapi.response.VerifyRunSummary;
import com.paytech.platform.support.integration.domain.verify.VerifyAccountView;
import com.paytech.platform.support.integration.domain.verify.VerifyCaseView;
import com.paytech.platform.support.integration.domain.verify.VerifyRunBriefView;
import com.paytech.platform.support.integration.domain.verify.VerifyRunDetailView;
import com.paytech.platform.support.integration.domain.verify.VerifyRunSummaryView;
import com.paytech.platform.support.integration.service.processor.request.GetIntegrationChildRequest;
import com.paytech.platform.support.integration.service.processor.request.ListVerifyRunsRequest;
import com.paytech.platform.support.integration.service.processor.request.SaveVerifyAccountRequest;
import com.paytech.platform.support.integration.service.processor.request.SaveVerifyCaseRequest;
import com.paytech.platform.support.integration.service.processor.request.StartVerifyRunRequest;

import lombok.AllArgsConstructor;

/** 沙箱验证管理的协议解析与投影；字段约束已由入口校验，这里只做规范化与类型转换。 */
@Component
@AllArgsConstructor
public class VerifyManagementConverter {

    /** 运行编号的最大长度。 */
    static final int RUN_CODE_LENGTH = 19;

    private final ExternalApiManagementConverter common;

    /** 创建验证账号。 */
    public SaveVerifyAccountRequest toCreateAccount(String actor, String session, String integrationCode,
        CreateVerifyAccountRequestBody body) {
        return new SaveVerifyAccountRequest(actor, session, common.code(integrationCode), common.code(body.getAccountCode()),
            name(body.getAccountName()), body.getChannelCode().strip(), body.getChannelAccountId().strip(),
            body.getEntityNo().strip(), body.getParameters(), body.getCredentials(), null, reason(body.getReason()));
    }

    /** 修改验证账号。 */
    public SaveVerifyAccountRequest toUpdateAccount(String actor, String session, String integrationCode,
        String accountCode, UpdateVerifyAccountRequestBody body) {
        return new SaveVerifyAccountRequest(actor, session, common.code(integrationCode), common.code(accountCode),
            name(body.getAccountName()), body.getChannelCode().strip(), body.getChannelAccountId().strip(),
            body.getEntityNo().strip(), body.getParameters(), body.getCredentials(), body.getExpectedVersion(),
            reason(body.getReason()));
    }

    /** 创建验证用例。 */
    public SaveVerifyCaseRequest toCreateCase(String actor, String session, String integrationCode,
        CreateVerifyCaseRequestBody body) {
        return new SaveVerifyCaseRequest(actor, session, common.code(integrationCode), common.code(body.getCaseCode()),
            name(body.getCaseName()), common.description(body.getDescription()), common.code(body.getAccountCode()),
            body.getSteps(), null, reason(body.getReason()));
    }

    /** 修改验证用例。 */
    public SaveVerifyCaseRequest toUpdateCase(String actor, String session, String integrationCode, String caseCode,
        UpdateVerifyCaseRequestBody body) {
        return new SaveVerifyCaseRequest(actor, session, common.code(integrationCode), common.code(caseCode),
            name(body.getCaseName()), common.description(body.getDescription()), common.code(body.getAccountCode()),
            body.getSteps(), body.getExpectedVersion(), reason(body.getReason()));
    }

    /** 只校验验证用例。 */
    public SaveVerifyCaseRequest toValidateCase(String actor, String session, String integrationCode,
        ValidateVerifyCaseRequestBody body) {
        return new SaveVerifyCaseRequest(actor, session, common.code(integrationCode), null, null, null,
            common.code(body.getAccountCode()), body.getSteps(), null, null);
    }

    /** 发起运行。 */
    public StartVerifyRunRequest toRunStart(String actor, String session, String integrationCode, String caseCode,
        StartVerifyRunRequestBody body) {
        return new StartVerifyRunRequest(actor, session, common.code(integrationCode), common.code(caseCode),
            reason(body.getReason()));
    }

    /** 运行详情：运行编号不按配置编码规则解析，由用例层判断。 */
    public GetIntegrationChildRequest toRunGet(String actor, String session, String integrationCode, String runCode) {
        String code = runCode == null ? "" : runCode.strip();
        return new GetIntegrationChildRequest(actor, session, common.code(integrationCode),
            code.length() > RUN_CODE_LENGTH ? "" : code);
    }

    /** 运行列表。 */
    public ListVerifyRunsRequest toRunList(String actor, String session, String integrationCode, String caseCode,
        Integer page, Integer size) {
        return new ListVerifyRunsRequest(actor, session, common.code(integrationCode),
            caseCode == null || caseCode.isBlank() ? null : common.code(caseCode),
            ClientApiManagementInputs.paging(page, size));
    }

    /** 账号列表项。 */
    public List<VerifyAccountSummary> toAccounts(List<VerifyAccountView> views) {
        return views.stream().map(view -> new VerifyAccountSummary(view.getAccountCode(), view.getAccountName(),
            view.getChannelCode(), view.getChannelAccountId(), view.getEntityNo(), view.getVersion(),
            view.getUpdateTime())).toList();
    }

    /** 账号详情。 */
    public VerifyAccountResponseBody toAccount(VerifyAccountView view) {
        return new VerifyAccountResponseBody(view.getAccountCode(), view.getAccountName(), view.getChannelCode(),
            view.getChannelAccountId(), view.getEntityNo(), view.getParameters(),
            view.getCredentials(), view.getReferencedCaseCodes(), view.getVersion(), view.getCreateTime(),
            view.getUpdateTime(), id(view.getUpdatedBy()));
    }

    /** 用例分页。 */
    public PagingResponseBody<VerifyCaseSummary> toCasePage(PagingResponseBody<VerifyCaseView> page) {
        return page.map(view -> new VerifyCaseSummary(view.getCaseCode(), view.getCaseName(), view.getAccountCode(),
            view.getStandardApiCodes(), view.getSteps() == null ? 0 : view.getSteps().size(),
            view.getVerifyStatus().getCode(), brief(view.getLatestRun()), view.getVersion(), view.getUpdateTime()));
    }

    /** 用例详情。 */
    public VerifyCaseResponseBody toCase(VerifyCaseView view) {
        return new VerifyCaseResponseBody(view.getCaseCode(), view.getCaseName(), view.getDescription(),
            view.getAccountCode(), view.getSteps(), view.getVerifyStatus().getCode(), brief(view.getLatestRun()),
            view.getVersion(), view.getCreateTime(), view.getUpdateTime(), id(view.getUpdatedBy()));
    }

    /** 已发起的运行。 */
    public VerifyRunStartResponseBody toRunStart(VerifyRunBriefView view) {
        return new VerifyRunStartResponseBody(Long.toString(view.getRunId()), view.getRunStatus().getCode());
    }

    /** 运行分页。 */
    public PagingResponseBody<VerifyRunSummary> toRunPage(PagingResponseBody<VerifyRunSummaryView> page) {
        return page.map(view -> new VerifyRunSummary(Long.toString(view.getRunId()), view.getCaseCode(),
            view.getCaseName(), view.getRunStatus().getCode(), view.getFailureSummary(), id(view.getStartedBy()),
            view.getStartTime(), view.getFinishTime()));
    }

    /** 运行详情。 */
    public VerifyRunResponseBody toRun(VerifyRunDetailView view) {
        var steps = view.getSteps().stream().map(step -> new VerifyRunStepDetail(step.getStepNo(), step.getStepKey(),
            step.getStandardApiCode(), step.getStepStatus().getCode(), step.getResolvedInput(), step.getResult(),
            step.getAssertions(), step.getTrace(), step.getElapsedMs())).toList();
        return new VerifyRunResponseBody(Long.toString(view.getRunId()), view.getCaseCode(),
            view.getRunStatus().getCode(), view.getFailureSummary(), view.getCaseSnapshot(), view.getConfigVersions(),
            view.getEndpoints(), view.isStale(), id(view.getStartedBy()), view.getStartTime(), view.getFinishTime(),
            steps);
    }

    static VerifyRunBrief brief(VerifyRunBriefView view) {
        return view == null ? null : new VerifyRunBrief(Long.toString(view.getRunId()), view.getRunStatus().getCode(),
            view.getStartTime(), view.getFinishTime());
    }

    static String id(Long value) {
        return value == null ? null : value.toString();
    }

    static String name(String value) {
        return ConfigurationInputs.text(value, 128, "name");
    }

    static String reason(String value) {
        return ConfigurationInputs.text(value, 500, "reason");
    }
}
