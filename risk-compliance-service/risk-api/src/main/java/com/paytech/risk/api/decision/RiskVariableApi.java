package com.paytech.risk.api.decision;

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
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.service.annotation.*;

/** 统一变量的私有 HTTP 边界；业务路径保持 v1。 */
@HttpExchange("/decision-variables/v1")
public interface RiskVariableApi {

    /** 读取工程映射能力；不返回替代变量库的静态候选。 */
    @GetExchange("/authoring-context")
    VariableManagementContextResponseBody getAuthoringContext(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "pointCode", required = false) String pointCode);

    /** 分页或指定编码批取当前变量；两种模式互斥。 */
    @GetExchange("/variables")
    VariableListResponseBody listVariables(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "sourceType", required = false) String sourceType,
        @RequestParam(value = "valueType", required = false) String valueType,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "pointCode", required = false) String pointCode,
        @RequestParam(value = "usage", required = false) String usage,
        @RequestParam(value = "nodeType", required = false) String nodeType,
        @RequestParam(value = "codes", required = false) String codes,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /** 按永久编码读取当前定义；停用和墓碑仍可读。 */
    @GetExchange("/variables/{variableCode}")
    VariableDetailResponseBody getVariable(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("variableCode") String variableCode);

    /** 创建变量并返回初始版本，成功 HTTP 201。 */
    @PostExchange("/variables")
    VariableDetailResponseBody createVariable(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody CreateVariableRequestBody body);

    /** 按期望版本更新允许的当前值及展示文字。 */
    @PutExchange("/variables/{variableCode}")
    VariableDetailResponseBody updateVariable(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("variableCode") String variableCode,
        @Valid @RequestBody UpdateVariableRequestBody body);

    /** 仅在所有当前和历史引用均不存在时逻辑删除，禁止请求 Body。 */
    @DeleteExchange("/variables/{variableCode}")
    DeleteVariableResponseBody deleteVariable(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("variableCode") String variableCode,
        @RequestParam(value = "expectedVersion", required = true) String expectedVersion);

    /** 成员翻页校验根版本，防止混合不同批次。 */
    @GetExchange("/variables/{variableCode}/members")
    VariableMembersResponseBody listMembers(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("variableCode") String variableCode,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "expectedVersion", required = false) String expectedVersion);

    /** 在根 CAS 保护下整批替换成员，失败全部回滚。 */
    @PutExchange("/variables/{variableCode}/members")
    ReplaceMembersResponseBody replaceMembers(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("variableCode") String variableCode,
        @Valid @RequestBody ReplaceMembersRequestBody body);

    /** 分段扫描精确引用，空页不代表没有后续引用。 */
    @GetExchange("/variables/{variableCode}/references")
    VariableReferencesResponseBody getReferences(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("variableCode") String variableCode,
        @RequestParam(value = "scope", required = false) String scope,
        @RequestParam(value = "cursor", required = false) String cursor,
        @RequestParam(value = "size", required = false) Integer size);
}
