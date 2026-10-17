package com.paytech.risk.decision.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.decision.api.response.*;

/** 决策流配置只读 HTTP 合同。 */
@HttpExchange("/decision-flows/v1")
public interface DecisionFlowQueryApi {

    /**
     * 读取决策点的流程配置能力和变量查询上下文。
     * 
     * @param actorAccountId
     *            可信操作者 Account ID
     * @param sessionId
     *            可信会话标识
     * @param pointCode
     *            可选决策点编码，缺省为 CHECKOUT_PREPARE
     * @return 配置目录与容量上限，不包含真实业务输入或执行结果
     */
    @GetExchange("/authoring-context")
    FlowAuthoringContextResponseBody authoringContext(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "pointCode", required = false) String pointCode);

    /**
     * 分页查询当前可配置流程，每个管理根一行。
     * 
     * @param actorAccountId
     *            可信操作者 Account ID
     * @param sessionId
     *            可信会话标识
     * @param pointCode
     *            必填决策点编码
     * @param flowCode
     *            可选永久流程编码，精确匹配
     * @param keyword
     *            可选当前名称，按字面包含匹配，1 至 100 个字符且无首尾空白
     * @param page
     *            可选页码，由公共分页规则校验
     * @param size
     *            可选每页条数，由公共分页规则校验
     * @return 按更新时间和流程 ID 倒序的当前配置摘要，不包含已删除流程
     */
    @GetExchange("/flows")
    PagingResponseBody<FlowListItemResponseBody> list(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "pointCode", required = true) String pointCode,
        @RequestParam(value = "flowCode", required = false) String flowCode,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 读取流程当前配置和同一快照中的共享变量当前值。
     * 
     * @param actorAccountId
     *            可信操作者 Account ID
     * @param sessionId
     *            可信会话标识
     * @param flowId
     *            正 BIGINT 十进制字符串，流程管理根身份
     * @return 当前准确版本；根不存在或已删除时返回未找到
     */
    @GetExchange("/flows/{flowId}")
    FlowDetailResponseBody current(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("flowId") String flowId);

    /**
     * 在固定业务版本上界内倒序浏览不可变历史。
     * 
     * @param actorAccountId
     *            可信操作者 Account ID
     * @param sessionId
     *            可信会话标识
     * @param flowId
     *            正 BIGINT 十进制字符串，流程管理根身份
     * @param maxVersionNo
     *            必填正数，不能超过当前最高业务版本
     * @param page
     *            可选页码，由公共分页规则校验
     * @param size
     *            可选每页条数，由公共分页规则校验
     * @return 固定上界历史分页；整体删除后仍可读取，后续保存不进入本次浏览
     */
    @GetExchange("/flows/{flowId}/versions")
    PagingResponseBody<FlowHistoryItemResponseBody> history(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("flowId") String flowId,
        @RequestParam(value = "maxVersionNo", required = true) Integer maxVersionNo,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 读取明确归属于流程的准确历史版本。
     * 
     * @param actorAccountId
     *            可信操作者 Account ID
     * @param sessionId
     *            可信会话标识
     * @param flowId
     *            正 BIGINT 十进制字符串，流程管理根身份
     * @param flowVersionId
     *            正 BIGINT 十进制字符串，必须属于该流程
     * @return 当前根事实、准确历史图和共享变量当前值；已删除根仍可读取准确历史
     */
    @GetExchange("/flows/{flowId}/versions/{flowVersionId}")
    FlowDetailResponseBody version(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("flowId") String flowId,
        @PathVariable("flowVersionId") String flowVersionId);

    /**
     * 根据已保存来源的真实节点绑定解析准确规则引用。
     * 
     * @param actorAccountId
     *            可信操作者 Account ID
     * @param sessionId
     *            可信会话标识
     * @param sourceType
     *            FLOW_VERSION 或 LEGACY_POINT
     * @param sourceVersionId
     *            真实来源的正 BIGINT 准确版本 ID
     * @param nodeKey
     *            来源中的稳定规则集节点键，不接受任意规则版本替代
     * @return 绑定的准确规则详情；允许原引用规则集已删除，来源或节点不匹配返回未找到
     */
    @GetExchange("/rule-set-reference")
    RuleSetReferenceResponseBody ruleSetReference(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "sourceType", required = true) String sourceType,
        @RequestParam(value = "sourceVersionId", required = true) String sourceVersionId,
        @RequestParam(value = "nodeKey", required = true) String nodeKey);

    /**
     * 将原 Point Version 的实际绑定投影为只读顺序图。
     * 
     * @param actorAccountId
     *            可信操作者 Account ID
     * @param sessionId
     *            可信会话标识
     * @param pointVersionId
     *            旧点配置的正 BIGINT 准确版本 ID
     * @return 保留原绑定顺序和默认决定的只读图，不分配或创建新 Flow 身份
     */
    @GetExchange("/legacy-point-versions/{pointVersionId}")
    LegacyPointResponseBody legacyPoint(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("pointVersionId") String pointVersionId);
}
