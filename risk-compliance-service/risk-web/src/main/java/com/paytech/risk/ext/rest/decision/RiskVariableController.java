package com.paytech.risk.ext.rest.decision;

import static com.paytech.risk.ext.rest.decision.RiskVariableWireConverter.*;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.api.decision.RiskVariableApi;
import com.paytech.risk.api.decision.response.VariableDetailResponseBody;
import com.paytech.risk.api.decision.response.VariableListResponseBody;
import com.paytech.risk.api.decision.response.VariableMembersResponseBody;
import com.paytech.risk.api.decision.response.ReplaceMembersResponseBody;
import com.paytech.risk.api.decision.response.DeleteVariableResponseBody;
import com.paytech.risk.api.decision.response.VariableReferencesResponseBody;
import com.paytech.risk.api.decision.response.VariableManagementContextResponseBody;
import com.paytech.risk.api.decision.request.CreateVariableRequestBody;
import com.paytech.risk.api.decision.request.UpdateVariableRequestBody;
import com.paytech.risk.api.decision.request.ReplaceMembersRequestBody;
import com.paytech.risk.api.decision.RiskVariableModels.*;
import com.paytech.risk.decision.domain.enums.*;
import com.paytech.risk.decision.domain.variable.DecisionSourceContracts;
import com.paytech.risk.decision.domain.variable.RiskVariable;
import com.paytech.risk.service.processor.RiskVariableManagementProcessor;
import com.paytech.risk.service.request.RiskOperationRequestContextParser;
import java.util.*;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 可信 Operations 元数据和统一变量协议适配，不访问数据库或开启事务。 */
@RestController
@Validated
@AllArgsConstructor
public class RiskVariableController implements RiskVariableApi {
    private final RiskOperationRequestContextParser parser;
    private final RiskVariableManagementProcessor processor;

    /** 读取当前工程映射能力，业务变量候选继续走目录接口。 */
    @Override public VariableManagementContextResponseBody getAuthoringContext(String actorAccountId, String sessionId, String pointCode) {
        parser.parse(actorAccountId, sessionId);
        return context(parser.parseOptionalUpperCode("pointCode", pointCode));
    }

    /** 将普通分页和精确编码批取互斥地转换为领域查询。 */
    @Override public VariableListResponseBody listVariables(String actorAccountId, String sessionId, String keyword,
        String sourceType, String valueType, String status, String pointCode, String usage, String nodeType, String codes, Integer page, Integer size) {
        parser.parse(actorAccountId, sessionId);
        RiskVariable.Query query = query(keyword, sourceType, valueType, status, pointCode, usage, nodeType, codes, page, size);
        var result = processor.list(query);
        return new VariableListResponseBody(result.items().stream().map(RiskVariableWireConverter::summary).toList(),
            codes == null ? query.page() : null, codes == null ? query.size() : null, result.total(), result.missingCodes(), time(result.valuesAsOf()));
    }

    /** 按永久编码读取当前定义，包括停用和墓碑。 */
    @Override public VariableDetailResponseBody getVariable(String actorAccountId, String sessionId, String variableCode) {
        parser.parse(actorAccountId, sessionId);
        var result = processor.detail(code(variableCode));
        return detail(result.value(), result.valuesAsOf());
    }

    /** 创建成功返回 201；集合初建为空，成员另通过原子替换维护。 */
    @Override @ResponseStatus(HttpStatus.CREATED)
    public VariableDetailResponseBody createVariable(String actorAccountId, String sessionId, CreateVariableRequestBody body) {
        var actor = parser.parse(actorAccountId, sessionId);
        var result = processor.create(code(body.variableCode()), create(body), actor.actorAccountId());
        return detail(result.value(), result.valuesAsOf());
    }

    /** 更新只允许已批准的可变字段，操作者从可信请求上下文取得。 */
    @Override public VariableDetailResponseBody updateVariable(String actorAccountId, String sessionId, String variableCode, UpdateVariableRequestBody body) {
        var actor = parser.parse(actorAccountId, sessionId);
        var command = new RiskVariable.Update(parser.parsePositiveId("expectedVersion", body.expectedVersion()), body.variableName(),
            body.description(), body.currentValue(), parse(VariableStatus.class, body.status()));
        var result = processor.update(code(variableCode), command, actor.actorAccountId());
        return detail(result.value(), result.valuesAsOf());
    }

    /** 删除由 Owner 在根锁后重新核对所有版本引用，不能使用页面查询作为证明。 */
    @Override public DeleteVariableResponseBody deleteVariable(String actorAccountId, String sessionId, String variableCode, String expectedVersion) {
        var actor = parser.parse(actorAccountId, sessionId);
        var result = processor.delete(code(variableCode), parser.parsePositiveId("expectedVersion", expectedVersion), actor.actorAccountId());
        return new DeleteVariableResponseBody(result.definition().variableCode(), result.status().getCode(), Long.toString(result.version()));
    }

    /** 成员分页保留首次根版本，冲突要求重新加载。 */
    @Override public VariableMembersResponseBody listMembers(String actorAccountId, String sessionId, String variableCode,
        String keyword, Integer page, Integer size, String expectedVersion) {
        parser.parse(actorAccountId, sessionId);
        int selectedPage = number(page, 1, 10000), selectedSize = number(size, 50, 100);
        var result = processor.members(code(variableCode), pattern(keyword), selectedPage, selectedSize,
            expectedVersion == null ? null : parser.parsePositiveId("expectedVersion", expectedVersion));
        return new VariableMembersResponseBody(variableCode, Long.toString(result.version()), result.items().stream()
            .map(m -> new OrderedMember(m.value(), m.displayName(), m.sortOrder())).toList(), selectedPage, selectedSize, result.total(), time(result.valuesAsOf()));
    }

    /** 原子替换成员，服务端重新校验每个值及重复规范键。 */
    @Override public ReplaceMembersResponseBody replaceMembers(String actorAccountId, String sessionId, String variableCode, ReplaceMembersRequestBody body) {
        var actor = parser.parse(actorAccountId, sessionId);
        var members = body.members().stream().map(m -> new RiskVariable.Member(m.value(), m.displayName(), 0)).toList();
        var result = processor.replaceMembers(code(variableCode), parser.parsePositiveId("expectedVersion", body.expectedVersion()), members, actor.actorAccountId());
        return new ReplaceMembersResponseBody(variableCode, Long.toString(result.version()), members.size(), time(result.updateTime()));
    }

    /** 返回可继续扫描的准确引用位置，不返回任意 URL。 */
    @Override public VariableReferencesResponseBody getReferences(String actorAccountId, String sessionId, String variableCode,
        String scope, String cursor, Integer size) {
        parser.parse(actorAccountId, sessionId);
        var result = processor.references(code(variableCode), scope == null ? "ALL" : scope, cursor, number(size, 20, 100));
        return new VariableReferencesResponseBody(result.items().stream().map(r -> new VariableReference(r.consumerType(), Long.toString(r.rootId()),
            r.versionId() == null ? null : r.versionId().toString(), r.versionNo(), r.code(), r.name(), r.scope(), r.location(), r.variableCode())).toList(),
            result.nextCursor(), result.hasMore(), result.scannedRows());
    }

    /** 列表请求互斥及用途上下文在入站边界一次校验。 */
    RiskVariable.Query query(String keyword, String source, String type, String status, String point, String usage,
        String nodeType, String codes, Integer page, Integer size) {
        List<String> selectedCodes = null;
        if (codes != null) {
            if (Arrays.asList(keyword, source, type, status, point, usage, nodeType, page, size).stream().anyMatch(Objects::nonNull)) {
                throw new InvalidParameterException("codes 批取与筛选或分页互斥");
            }
            selectedCodes = Arrays.stream(codes.split(",", -1)).map(this::code).toList();
            if (selectedCodes.size() > 100 || new HashSet<>(selectedCodes).size() != selectedCodes.size()) {
                throw new InvalidParameterException("批取最多 100 个不重复变量编码");
            }
        }
        validateUsage(point, usage, nodeType);
        return new RiskVariable.Query(pattern(keyword), parse(VariableSource.class, source), parse(DecisionValueType.class, type),
            parse(VariableStatus.class, status), point, parse(VariableUsage.class, usage), nodeType, selectedCodes,
            number(page, 1, 10000), number(size, 20, 100));
    }

    /** 条件用途必须携带受支持点，节点输出仅通过准确节点类型筛选。 */
    void validateUsage(String point, String usage, String nodeType) {
        if (point != null && !DecisionSourceContracts.supports(code(point))) { throw new InvalidParameterException("决策点尚未支持"); }
        if (usage != null && point == null) { throw new InvalidParameterException("条件用途须指定 pointCode"); }
        if (nodeType != null && (!"FLOW_CONDITION".equals(usage) || !Set.of("START", "RULE_SET").contains(nodeType))) {
            throw new InvalidParameterException("nodeType 仅用于流程出线条件");
        }
    }

    /** 字面包含搜索转义 SQL 通配符，不接受控制字符。 */
    String pattern(String keyword) {
        if (keyword == null) { return null; }
        if (keyword.codePointCount(0, keyword.length()) > 128 || keyword.codePoints().anyMatch(Character::isISOControl)) {
            throw new InvalidParameterException("搜索关键字无效");
        }
        return "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
    /** 本合同页大小为 1–100，查询范围只补缺省，不截断用户输入。 */
    int number(Integer value, int defaultValue, int maximum) {
        int result = value == null ? defaultValue : value;
        if (result < 1 || result > maximum) { throw new InvalidParameterException("分页参数超出允许范围"); }
        return result;
    }
    /** 规范编码在进入资源锁方法之前完成验证。 */
    String code(String value) { return parser.parseRequiredUpperCode("variableCode", value); }
}
