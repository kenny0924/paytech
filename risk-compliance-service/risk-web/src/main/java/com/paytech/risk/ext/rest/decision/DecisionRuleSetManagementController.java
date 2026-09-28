package com.paytech.risk.ext.rest.decision;

import static com.paytech.risk.service.convert.DecisionRuleSetExceptionConverter.mapped;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.api.decision.DecisionRuleSetManagementApi;
import com.paytech.risk.api.decision.request.CreateRuleSetVersionRequestBody;
import com.paytech.risk.api.decision.request.DeleteRuleSetRequestBody;
import com.paytech.risk.api.decision.request.FreezeRuleSetVersionRequestBody;
import com.paytech.risk.api.decision.response.CreateRuleSetVersionResponseBody;
import com.paytech.risk.api.decision.response.DeleteRuleSetResponseBody;
import com.paytech.risk.api.decision.response.FreezeRuleSetVersionResponseBody;
import com.paytech.risk.api.decision.response.RuleAuthoringContextResponseBody;
import com.paytech.risk.api.decision.response.RuleSetDetailResponseBody;
import com.paytech.risk.api.decision.response.RuleSetListItemResponseBody;
import com.paytech.risk.decision.service.processor.CreateRuleSetVersionProcessor;
import com.paytech.risk.decision.service.processor.DeleteRuleSetProcessor;
import com.paytech.risk.decision.service.processor.FreezeRuleSetVersionProcessor;
import com.paytech.risk.decision.service.processor.RuleAuthoringContextProcessor;
import com.paytech.risk.decision.service.processor.RuleSetDetailProcessor;
import com.paytech.risk.decision.service.processor.RuleSetListProcessor;
import com.paytech.risk.decision.service.processor.request.CreateRuleSetVersionRequest;
import com.paytech.risk.decision.service.processor.request.DeleteRuleSetRequest;
import com.paytech.risk.decision.service.processor.request.FreezeRuleSetVersionRequest;
import com.paytech.risk.decision.service.processor.request.RuleAuthoringContextRequest;
import com.paytech.risk.decision.service.processor.request.RuleSetDetailRequest;
import com.paytech.risk.decision.service.processor.request.RuleSetListRequest;
import com.paytech.risk.service.request.RiskOperationRequestContextParser;
import lombok.AllArgsConstructor;

/** 规则集 HTTP 边界：解析可信身份、转换协议并调用 Owner 用例。 */
@RestController
@Validated
@AllArgsConstructor
public class DecisionRuleSetManagementController implements DecisionRuleSetManagementApi {
    private final RuleSetListProcessor listProcessor;
    private final DeleteRuleSetProcessor deleteProcessor;
    private final RuleSetDetailProcessor detailProcessor;
    private final CreateRuleSetVersionProcessor createProcessor;
    private final FreezeRuleSetVersionProcessor freezeProcessor;
    private final RuleAuthoringContextProcessor authoringProcessor;
    private final RiskOperationRequestContextParser contextParser;

    /** 读取配置能力目录。 */
    @Override
    public RuleAuthoringContextResponseBody getAuthoringContext(String actorAccountId, String sessionId, String pointCode) {
        contextParser.parse(actorAccountId, sessionId);
        return mapped(() -> RiskDecisionWireConverter.toResponse(authoringProcessor.process(new RuleAuthoringContextRequest(
            contextParser.parseRequiredUpperCode("pointCode", pointCode)))));
    }

    /** 查询配置头分页与使用状态。 */
    @Override
    public PagingResponseBody<RuleSetListItemResponseBody> listRuleSets(String actorAccountId, String sessionId,
        Integer page, Integer size, String pointCode, String ruleSetCode, String status) {
        contextParser.parse(actorAccountId, sessionId);
        var paging = PagingRequestBody.of(page, size);
        return mapped(() -> RiskDecisionWireConverter.toResponse(listProcessor.process(new RuleSetListRequest(paging.page(),
            paging.size(), contextParser.parseOptionalUpperCode("pointCode", pointCode),
            contextParser.parseOptionalUpperCode("ruleSetCode", ruleSetCode),
            RiskDecisionWireConverter.parseRuleSetStatus(contextParser.parseOptionalUpperCode("status", status))))));
    }

    /** 不可变规则详情与当前变量同快照读取。 */
    @Override
    public RuleSetDetailResponseBody getRuleSet(String actorAccountId, String sessionId, String ruleSetVersionId) {
        contextParser.parse(actorAccountId, sessionId);
        return mapped(() -> {
            var result = detailProcessor.process(new RuleSetDetailRequest(contextParser.parsePositiveId("ruleSetVersionId", ruleSetVersionId)));
            return RiskDecisionWireConverter.toResponse(result.aggregate(), result.catalog());
        });
    }

    /** 保存内容不依赖 HTTP 幂等键，业务基准由 Owner 校验。 */
    @Override
    public CreateRuleSetVersionResponseBody createRuleSetVersion(String actorAccountId, String sessionId,
        CreateRuleSetVersionRequestBody requestBody) {
        var actor = contextParser.parse(actorAccountId, sessionId);
        return mapped(() -> RiskDecisionWireConverter.toResponse(createProcessor.process(new CreateRuleSetVersionRequest(
            actor.actorAccountId(), RiskDecisionWireConverter.toDomain(requestBody)))));
    }

    /** 按准确规则版本和内容 Hash 请求冻结。 */
    @Override
    public FreezeRuleSetVersionResponseBody freezeRuleSetVersion(String actorAccountId, String sessionId,
        String ruleSetVersionId, FreezeRuleSetVersionRequestBody requestBody) {
        var actor = contextParser.parse(actorAccountId, sessionId);
        return mapped(() -> RiskDecisionWireConverter.toResponse(freezeProcessor.process(new FreezeRuleSetVersionRequest(
            actor.actorAccountId(), contextParser.parsePositiveId("ruleSetVersionId", ruleSetVersionId), requestBody.getExpectedContentHash()))));
    }

    /** 删除使用整体根身份及调用者确认的管理版本。 */
    @Override
    public DeleteRuleSetResponseBody deleteRuleSet(String actorAccountId, String sessionId, String ruleSetId,
        DeleteRuleSetRequestBody requestBody) {
        var actor = contextParser.parse(actorAccountId, sessionId);
        return mapped(() -> RiskDecisionWireConverter.toResponse(deleteProcessor.process(new DeleteRuleSetRequest(
            actor.actorAccountId(), contextParser.parsePositiveId("ruleSetId", ruleSetId),
            contextParser.parsePositiveId("expectedVersion", requestBody.getExpectedVersion())))));
    }
}
