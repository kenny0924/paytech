package com.paytech.risk.api.decision;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.*;

import com.paytech.risk.api.decision.request.CreateVariableRequestBody;
import com.paytech.risk.api.decision.request.ReplaceMembersRequestBody;
import com.paytech.risk.api.decision.request.UpdateVariableRequestBody;
import com.paytech.risk.api.decision.response.*;

import jakarta.validation.Valid;

/**
 * 统一风控变量的私网管理合同
 * <li>GET/DELETE 不接受 Body；未知、重复或空 Query 参数一律拒绝。</li>
 * <li>普通变量维护不使用 Idempotency-Key，以唯一编码和根版本 CAS 处理重复与并发。</li>
 */
@HttpExchange("/decision-variables/v1")
public interface RiskVariableApi {

    /**
     * 读取工程已接通的变量编排能力
     *
     * @param actorAccountId
     *            可信操作者 Account ID
     * @param sessionId
     *            可信会话标识
     * @param pointCode
     *            可选决策点；缺省返回全部已支持点及来源合同
     * @return 已支持点、来源能力、值类型、格式、量纲、容量上限及准确来源合同
     *         <li>不返回可脱离变量库直接保存的字段目录；未支持的决策点返回 400。</li>
     */
    @GetExchange("/authoring-context")
    VariableManagementContextResponseBody getAuthoringContext(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "pointCode", required = false) String pointCode);

    /**
     * 分页查询变量目录，或按编码批取指定定义
     *
     * @param actorAccountId
     *            可信操作者 Account ID
     * @param sessionId
     *            可信会话标识
     * @param keyword
     *            编码或名称字面包含，最长 128；可空
     * @param sourceType
     *            来源类型准确 Code；可空
     * @param valueType
     *            值类型准确 Code；可空
     * @param status
     *            状态准确 Code；缺省不含 DELETED
     * @param pointCode
     *            决策点；指定 usage 时必填
     * @param usage
     *            引用用途；用于条件选项时与 pointCode 一起传入
     * @param nodeType
     *            节点类型；仅在 usage=FLOW_CONDITION 时允许 START 或 RULE_SET
     * @param codes
     *            逗号分隔的最多 100 个不重复编码；与其他筛选及分页互斥
     * @param page
     *            从 1 开始的页码，默认 1，最大 10000
     * @param size
     *            每页数量，默认 20，范围 1–100
     * @return 变量目录及当前值读取时间
     *         <li>普通分页返回 page/size/total；编码批取不返回分页字段，改为返回 missingCodes。</li>
     *         <li>批取包含停用和墓碑定义，不代表可绕过保存时的作用域校验。</li>
     */
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

    /**
     * 按永久编码读取变量当前定义
     *
     * @param actorAccountId
     *            可信操作者 Account ID
     * @param sessionId
     *            可信会话标识
     * @param variableCode
     *            永久变量编码
     * @return 当前定义、有限成员预览及可选值集合预览
     *         <li>停用和墓碑仍可读取；编码不存在返回 404 VARIABLE_NOT_FOUND。</li>
     */
    @GetExchange("/variables/{variableCode}")
    VariableDetailResponseBody getVariable(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("variableCode") String variableCode);

    /**
     * 创建变量并返回初始版本
     *
     * @param actorAccountId
     *            可信操作者 Account ID
     * @param sessionId
     *            可信会话标识
     * @param body
     *            完整变量定义；不适用字段显式为 null，请求不超过 16 KiB
     * @return 新变量详情，HTTP 201
     *         <li>集合创建为空集，成员通过整批替换接口维护。</li>
     *         <li>编码已存在（含已删除）返回 409 VARIABLE_CODE_EXISTS。</li>
     */
    @PostExchange("/variables")
    VariableDetailResponseBody createVariable(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody CreateVariableRequestBody body);

    /**
     * 按期望根版本更新可变字段
     *
     * @param actorAccountId
     *            可信操作者 Account ID
     * @param sessionId
     *            可信会话标识
     * @param variableCode
     *            永久变量编码
     * @param body
     *            名称、说明、常量当前值、状态及期望根版本；请求不超过 16 KiB
     * @return 更新后的变量详情
     *         <li>结构字段不可修改；系统定义只允许修改名称和说明。</li>
     *         <li>根版本不一致返回 409 VARIABLE_VERSION_CONFLICT，不自动以最新版本重试。</li>
     */
    @PutExchange("/variables/{variableCode}")
    VariableDetailResponseBody updateVariable(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("variableCode") String variableCode,
        @Valid @RequestBody UpdateVariableRequestBody body);

    /**
     * 在无任何当前或历史引用时逻辑删除变量
     *
     * @param actorAccountId
     *            可信操作者 Account ID
     * @param sessionId
     *            可信会话标识
     * @param variableCode
     *            永久变量编码
     * @param expectedVersion
     *            期望根版本，正整数十进制字符串
     * @return 墓碑编码、DELETED 状态及新根版本
     *         <li>存在引用返回 409 VARIABLE_IN_USE；编码保留且不可复用。</li>
     */
    @DeleteExchange("/variables/{variableCode}")
    DeleteVariableResponseBody deleteVariable(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("variableCode") String variableCode,
        @RequestParam(value = "expectedVersion", required = true) String expectedVersion);

    /**
     * 分页读取集合成员
     *
     * @param actorAccountId
     *            可信操作者 Account ID
     * @param sessionId
     *            可信会话标识
     * @param variableCode
     *            集合变量编码
     * @param keyword
     *            规范值或显示名称字面包含，最长 128；可空
     * @param page
     *            从 1 开始的页码，默认 1，最大 10000
     * @param size
     *            每页数量，默认 50，范围 1–100
     * @param expectedVersion
     *            首次查询返回的根版本；第 1 页可省略，后续页必填
     * @return 根版本、成员页及当前值读取时间
     *         <li>根版本变化返回 409，防止翻页混合不同批次；非集合返回 409。</li>
     */
    @GetExchange("/variables/{variableCode}/members")
    VariableMembersResponseBody listMembers(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("variableCode") String variableCode,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "expectedVersion", required = false) String expectedVersion);

    /**
     * 在根版本保护下整批替换集合成员
     *
     * @param actorAccountId
     *            可信操作者 Account ID
     * @param sessionId
     *            可信会话标识
     * @param variableCode
     *            集合变量编码
     * @param body
     *            期望根版本及 0–1000 个成员；请求不超过 64 KiB
     * @return 新根版本、成员数量与更新时间
     *         <li>任一成员格式错误或规范值重复使整批失败，不部分写入。</li>
     */
    @PutExchange("/variables/{variableCode}/members")
    ReplaceMembersResponseBody replaceMembers(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("variableCode") String variableCode,
        @Valid @RequestBody ReplaceMembersRequestBody body);

    /**
     * 分段扫描变量的精确引用位置
     *
     * @param actorAccountId
     *            可信操作者 Account ID
     * @param sessionId
     *            可信会话标识
     * @param variableCode
     *            永久变量编码
     * @param scope
     *            引用范围 ALL / CURRENT / HISTORY，默认 ALL
     * @param cursor
     *            上一页返回的续扫游标，最长 2048；首次查询为空
     * @param size
     *            本页最多返回的引用数，默认 20，范围 1–100
     * @return 引用位置、续扫游标、是否还有后续及本页扫描行数
     *         <li>某页可以为空且 hasMore=true，调用方必须继续扫描，不能视为无引用。</li>
     */
    @GetExchange("/variables/{variableCode}/references")
    VariableReferencesResponseBody getReferences(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("variableCode") String variableCode,
        @RequestParam(value = "scope", required = false) String scope,
        @RequestParam(value = "cursor", required = false) String cursor,
        @RequestParam(value = "size", required = false) Integer size);
}
