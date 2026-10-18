package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.RiskFlowModels.*;

/** Risk 的决策流配置适配端口，输出 BFF 自有模型，不拥有图业务规则。 */
public interface RiskFlowClient {
    FlowAuthoringContextResponseBody authoringContext(OperatorContext o, String pointCode);
    PagingResponseBody<FlowListItemResponseBody> list(OperatorContext o, String pointCode, String flowCode, String keyword, int page, int size);
    FlowDetailResponseBody current(OperatorContext o, String flowId);
    PagingResponseBody<FlowHistoryItemResponseBody> history(OperatorContext o, String flowId, int maxVersionNo, int page, int size);
    FlowDetailResponseBody version(OperatorContext o, String flowId, String flowVersionId);
    RuleSetReferenceResponseBody ruleSetReference(OperatorContext o, String sourceType, String sourceVersionId, String nodeKey);
    LegacyPointResponseBody legacyPoint(OperatorContext o, String pointVersionId);
    SaveFlowResponseBody save(OperatorContext o, SaveFlowRequestBody body);
    ValidationResponseBody validate(OperatorContext o, SaveFlowRequestBody body);
    DeleteFlowResponseBody delete(OperatorContext o, String flowId, String expectedVersion);
}
