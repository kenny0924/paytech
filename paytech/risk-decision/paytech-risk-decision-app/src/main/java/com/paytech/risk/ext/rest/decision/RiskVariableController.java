package com.paytech.risk.ext.rest.decision;

import static com.paytech.risk.service.convert.RiskVariableExceptionConverter.mapped;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.risk.decision.api.RiskVariableApi;
import com.paytech.risk.decision.api.request.CreateVariableRequestBody;
import com.paytech.risk.decision.api.request.ReplaceMembersRequestBody;
import com.paytech.risk.decision.api.request.UpdateVariableRequestBody;
import com.paytech.risk.decision.api.response.DeleteVariableResponseBody;
import com.paytech.risk.decision.api.response.ReplaceMembersResponseBody;
import com.paytech.risk.decision.api.response.VariableDetailResponseBody;
import com.paytech.risk.decision.api.response.VariableListResponseBody;
import com.paytech.risk.decision.api.response.VariableManagementContextResponseBody;
import com.paytech.risk.decision.api.response.VariableMembersResponseBody;
import com.paytech.risk.decision.api.response.VariableReferencesResponseBody;
import com.paytech.risk.decision.domainservice.policy.VariableQueryPolicy;
import com.paytech.risk.decision.service.processor.CreateVariableProcessor;
import com.paytech.risk.decision.service.processor.DeleteVariableProcessor;
import com.paytech.risk.decision.service.processor.ReplaceVariableMembersProcessor;
import com.paytech.risk.decision.service.processor.UpdateVariableProcessor;
import com.paytech.risk.decision.service.processor.VariableAuthoringContextProcessor;
import com.paytech.risk.decision.service.processor.VariableDetailProcessor;
import com.paytech.risk.decision.service.processor.VariableListProcessor;
import com.paytech.risk.decision.service.processor.VariableMembersProcessor;
import com.paytech.risk.decision.service.processor.VariableReferencesProcessor;
import com.paytech.risk.decision.service.processor.request.CreateVariableRequest;
import com.paytech.risk.decision.service.processor.request.DeleteVariableRequest;
import com.paytech.risk.decision.service.processor.request.ReplaceVariableMembersRequest;
import com.paytech.risk.decision.service.processor.request.UpdateVariableRequest;
import com.paytech.risk.decision.service.processor.request.VariableAuthoringContextRequest;
import com.paytech.risk.decision.service.processor.request.VariableDetailRequest;
import com.paytech.risk.decision.service.processor.request.VariableListRequest;
import com.paytech.risk.decision.service.processor.request.VariableMembersRequest;
import com.paytech.risk.decision.service.processor.request.VariableReferencesRequest;
import com.paytech.foundation.web.request.OperationRequestContextParser;

import lombok.AllArgsConstructor;

/** 统一变量私网 HTTP 入口；先解析可信操作者，再委托 Owner Processor，不访问数据库或开启事务。 */
@RestController
@AllArgsConstructor
public class RiskVariableController implements RiskVariableApi {
    private final VariableListProcessor listProcessor;
    private final VariableDetailProcessor detailProcessor;
    private final CreateVariableProcessor createProcessor;
    private final DeleteVariableProcessor deleteProcessor;
    private final UpdateVariableProcessor updateProcessor;
    private final VariableMembersProcessor membersProcessor;
    private final VariableReferencesProcessor referencesProcessor;
    private final ReplaceVariableMembersProcessor replaceMembersProcessor;
    private final OperationRequestContextParser context;
    private final VariableAuthoringContextProcessor authoringProcessor;

    /** 读取工程编排能力，业务变量候选继续走目录接口。 */
    @Override
    public VariableManagementContextResponseBody getAuthoringContext(String actorAccountId, String sessionId, String pointCode) {
        context.parse(actorAccountId, sessionId);
        var request = new VariableAuthoringContextRequest(context.parseOptionalUpperCode("pointCode", pointCode));
        return RiskVariableWireConverter.authoring(mapped(() -> authoringProcessor.process(request)));
    }

    /** 透传目录筛选，互斥模式与用途上下文由 Owner 查询规则校验。 */
    @Override
    public VariableListResponseBody listVariables(String actorAccountId, String sessionId, String keyword, String sourceType,
        String valueType, String status, String pointCode, String usage, String nodeType, String codes, Integer page, Integer size) {
        context.parse(actorAccountId, sessionId);
        var request = new VariableListRequest(keyword, sourceType, valueType, status, pointCode, usage, nodeType, codes, page, size);
        return RiskVariableWireConverter.list(mapped(() -> listProcessor.process(request)));
    }

    /** 按永久编码读取当前定义，包括停用和墓碑。 */
    @Override
    public VariableDetailResponseBody getVariable(String actorAccountId, String sessionId, String variableCode) {
        context.parse(actorAccountId, sessionId);
        var request = new VariableDetailRequest(code(variableCode));
        return RiskVariableWireConverter.detail(mapped(() -> detailProcessor.process(request)));
    }

    /** 创建成功返回 201；集合初建为空，成员另通过原子替换维护。 */
    @Override
    @ResponseStatus(HttpStatus.CREATED)
    public VariableDetailResponseBody createVariable(String actorAccountId, String sessionId, CreateVariableRequestBody body) {
        var actor = context.parse(actorAccountId, sessionId);
        var request = new CreateVariableRequest(actor.getActorAccountId(), RiskVariableWireConverter.create(body));
        return RiskVariableWireConverter.detail(mapped(() -> createProcessor.process(request)));
    }

    /** 更新只允许已批准的可变字段，操作者从可信请求上下文取得。 */
    @Override
    public VariableDetailResponseBody updateVariable(String actorAccountId, String sessionId, String variableCode,
        UpdateVariableRequestBody body) {
        var actor = context.parse(actorAccountId, sessionId);
        var command = RiskVariableWireConverter.update(body, context.parsePositiveId("expectedVersion", body.getExpectedVersion()));
        var request = new UpdateVariableRequest(actor.getActorAccountId(), code(variableCode), command);
        return RiskVariableWireConverter.detail(mapped(() -> updateProcessor.process(request)));
    }

    /** 删除由 Owner 在根写锁后重新核对全部版本引用，不使用页面查询作为证明。 */
    @Override
    public DeleteVariableResponseBody deleteVariable(String actorAccountId, String sessionId, String variableCode,
        String expectedVersion) {
        var actor = context.parse(actorAccountId, sessionId);
        var request = new DeleteVariableRequest(actor.getActorAccountId(), code(variableCode),
            context.parsePositiveId("expectedVersion", expectedVersion));
        return RiskVariableWireConverter.deleted(mapped(() -> deleteProcessor.process(request)));
    }

    /** 成员分页补齐合同默认页码，后续页由 Owner 校验首次根版本。 */
    @Override
    public VariableMembersResponseBody listMembers(String actorAccountId, String sessionId, String variableCode, String keyword,
        Integer page, Integer size, String expectedVersion) {
        context.parse(actorAccountId, sessionId);
        var request = new VariableMembersRequest(code(variableCode), keyword, VariableQueryPolicy.number(page, 1, 10000),
            VariableQueryPolicy.number(size, 50, 100),
            expectedVersion == null ? null : context.parsePositiveId("expectedVersion", expectedVersion));
        var result = mapped(() -> membersProcessor.process(request));
        return RiskVariableWireConverter.memberPage(result, request.getPage(), request.getSize());
    }

    /** 原子替换成员，服务端重新校验每个值及重复规范键。 */
    @Override
    public ReplaceMembersResponseBody replaceMembers(String actorAccountId, String sessionId, String variableCode,
        ReplaceMembersRequestBody body) {
        var actor = context.parse(actorAccountId, sessionId);
        var request = new ReplaceVariableMembersRequest(actor.getActorAccountId(), code(variableCode),
            context.parsePositiveId("expectedVersion", body.getExpectedVersion()), RiskVariableWireConverter.members(body));
        var result = mapped(() -> replaceMembersProcessor.process(request));
        return RiskVariableWireConverter.replaced(result, request.getMembers().size());
    }

    /** 返回可继续扫描的准确引用位置，不返回任意 URL。 */
    @Override
    public VariableReferencesResponseBody getReferences(String actorAccountId, String sessionId, String variableCode,
        String scope, String cursor, Integer size) {
        context.parse(actorAccountId, sessionId);
        var request = new VariableReferencesRequest(code(variableCode), scope == null ? "ALL" : scope, cursor,
            VariableQueryPolicy.number(size, 20, 100));
        return RiskVariableWireConverter.references(mapped(() -> referencesProcessor.process(request)));
    }

    /** 路径编码在进入资源锁之前按规范大写编码解析。 */
    String code(String variableCode) {
        return context.parseRequiredUpperCode("variableCode", variableCode);
    }
}
