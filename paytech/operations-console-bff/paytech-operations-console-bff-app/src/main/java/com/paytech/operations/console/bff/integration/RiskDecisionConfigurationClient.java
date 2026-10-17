package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.*;
import com.paytech.operations.console.bff.service.model.RiskTestCandidateModels.*;

/** Operations 本地端口；每次调用只访问一个 Risk Owner 接口。 */
public interface RiskDecisionConfigurationClient {
    RuleAuthoringContextResponseBody authoringContext(OperatorContext context, String pointCode);

    PagingResponseBody<RuleSetListItemResponseBody> ruleSets(OperatorContext context, int page, int size,
        RuleSetQueryRequestBody query);

    RuleSetDetailResponseBody ruleSet(OperatorContext context, String ruleSetVersionId);

    CreateRuleSetVersionResponseBody createRuleSetVersion(OperatorContext context, CreateRuleSetVersionRequestBody request);

    /** 经当前授权后向 Risk 转发一次整体删除命令，不自动重试。 */
    DeleteRuleSetResponseBody deleteRuleSet(OperatorContext context, String ruleSetId, DeleteRuleSetRequestBody request);

    FreezeRuleSetVersionResponseBody freezeRuleSetVersion(OperatorContext context, String ruleSetVersionId, FreezeRuleSetVersionRequestBody request);

    PagingResponseBody<TestCandidateListItemResponseBody> candidates(OperatorContext context, int page, int size,
        CandidateQueryRequestBody query);

    TestCandidateDetailResponseBody candidate(OperatorContext context, String releaseId);

    CreateTestCandidateResponseBody createCandidate(OperatorContext context,
        CreateTestCandidateRequestBody request);

    ValidateTestCandidateResponseBody validateCandidate(OperatorContext context,
        String releaseId, ValidateTestCandidateRequestBody request);
}
