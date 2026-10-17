package com.paytech.risk.decision.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.decision.api.request.CreateRuleSetVersionRequestBody;
import com.paytech.risk.decision.api.request.DeleteRuleSetRequestBody;
import com.paytech.risk.decision.api.request.FreezeRuleSetVersionRequestBody;
import com.paytech.risk.decision.api.response.*;

import jakarta.validation.Valid;

/** Risk Rule Set 配置管理内部 HTTP 合同。 */
@HttpExchange("/decision-rule-sets/v1")
public interface DecisionRuleSetManagementApi {

    /**
     * 读取指定决策事件点的配置能力及当前系统变量。
     * <p>
     * 目录漂移时失败，不返回默认动作或变量。
     * </p>
     *
     * @param actorAccountId
     *            可信 Operations 操作者 ID
     * @param sessionId
     *            可信会话 ID
     * @param pointCode
     *            批准的决策事件点编码
     * @return 配置能力目录
     */
    @GetExchange("/authoring-context/{pointCode}")
    RuleAuthoringContextResponseBody getAuthoringContext(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("pointCode") String pointCode);

    /**
     * 分页查询未删除规则集的当前配置头。
     * <p>
     * inUse 表示任一历史版本被活动发布使用，不代表用户权限。
     * </p>
     *
     * @param actorAccountId
     *            可信 Operations 操作者 ID
     * @param sessionId
     *            可信会话 ID
     * @param page
     *            页码，省略按公共分页默认值
     * @param size
     *            每页条数，省略按公共分页默认值
     * @param pointCode
     *            可选决策事件点编码
     * @param ruleSetCode
     *            可选规则集编码
     * @param status
     *            可选业务版本状态
     * @return 当前配置头分页，含整体管理 ID、管理版本和使用状态
     */
    @GetExchange("/rule-sets")
    PagingResponseBody<RuleSetListItemResponseBody> listRuleSets(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "pointCode", required = false) String pointCode,
        @RequestParam(value = "ruleSetCode", required = false) String ruleSetCode,
        @RequestParam(value = "status", required = false) String status);

    /**
     * 读取准确规则版本及当前变量展示值。
     * <p>
     * 已删除管理根返回不存在；当前变量不参与历史内容 Hash。
     * </p>
     *
     * @param actorAccountId
     *            可信 Operations 操作者 ID
     * @param sessionId
     *            可信会话 ID
     * @param ruleSetVersionId
     *            正十进制规则版本 ID
     * @return 准确版本详情，新配置的 pointVersionId 为 null
     */
    @GetExchange("/rule-sets/{ruleSetVersionId}")
    RuleSetDetailResponseBody getRuleSet(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("ruleSetVersionId") String ruleSetVersionId);

    /**
     * 保存规则集的新 DRAFT 版本。
     * <p>
     * 要求 VARIABLE_REFERENCE；已有规则集必须提供准确基准版本和 Hash。请求幂等由 Gateway 负责，重复直调按业务编码和版本冲突处理。
     * </p>
     *
     * @param actorAccountId
     *            可信 Operations 操作者 ID
     * @param sessionId
     *            可信会话 ID
     * @param requestBody
     *            新配置内容、基准版本和变更原因
     * @return 新版本事实；新配置不创建旧执行编排，pointVersionId 为 null
     */
    @PostExchange("/rule-sets")
    CreateRuleSetVersionResponseBody createRuleSetVersion(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody CreateRuleSetVersionRequestBody requestBody);

    /**
     * 冻结当前 DRAFT 规则版本并递增整体管理版本。
     * <p>
     * 只有内容 Hash 一致且具备旧执行资格的版本可冻结；统一变量和附加动作配置不可冻结。请求幂等由 Gateway 负责。
     * </p>
     *
     * @param actorAccountId
     *            可信 Operations 操作者 ID
     * @param sessionId
     *            可信会话 ID
     * @param ruleSetVersionId
     *            正十进制当前规则版本 ID
     * @param requestBody
     *            调用方读取的 expectedContentHash
     * @return 首次冻结事实；重复直调按当前状态校验
     */
    @PostExchange("/rule-sets/{ruleSetVersionId}/freeze")
    FreezeRuleSetVersionResponseBody freezeRuleSetVersion(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("ruleSetVersionId") String ruleSetVersionId,
        @Valid @RequestBody FreezeRuleSetVersionRequestBody requestBody);

    /**
     * 整体删除未被活动发布使用的规则集。
     * <p>
     * 要求准确管理版本，保留全部历史内容与编码；删除后不可继续修改。请求幂等由 Gateway 负责，重复直调返回已删除冲突。
     * </p>
     *
     * @param actorAccountId
     *            可信 Operations 操作者 ID
     * @param sessionId
     *            可信会话 ID
     * @param ruleSetId
     *            规则集整体 ID，不是业务版本 ID
     * @param requestBody
     *            删除确认时读取的 expectedVersion
     * @return 删除后的管理版本、操作者和时间
     */
    @DeleteExchange("/rule-sets/{ruleSetId}")
    DeleteRuleSetResponseBody deleteRuleSet(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("ruleSetId") String ruleSetId,
        @Valid @RequestBody DeleteRuleSetRequestBody requestBody);
}
