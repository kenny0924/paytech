package com.paytech.risk.api.decision;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.api.decision.response.RuleSetHistoryItemResponseBody;
import com.paytech.risk.api.decision.response.RuleSetReleaseReferencesResponseBody;
/** 历史与发布引用查询，Path 使用整体根 ID。 */
@HttpExchange("/decision-rule-sets/v1/rule-sets")
public interface DecisionConfigurationQueryApi {
    /**
     * 查询规则集固定版本上界内的历史分页。
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param ruleSetId
     *            整体规则根 ID，不是版本 ID；不存在或整体删除返回 404
     * @param maxVersionNo
     *            必填的正整数上界，不得超过查询时的当前业务版本
     * @param page
     *            页码，缺省为 1，范围为 1–100000
     * @param size
     *            每页条数，缺省为 10，只允许 10、20、50、100、200
     * @return 按业务版本号、版本 ID 倒序的历史页；当前标记取自同一快照内的根指针
     */
    @GetExchange("/{ruleSetId}/versions")
    PagingResponseBody<RuleSetHistoryItemResponseBody> history(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("ruleSetId") String ruleSetId,
        @RequestParam("maxVersionNo") Integer maxVersionNo,
        @RequestParam(value = "page", required = false) Integer page, @RequestParam(value = "size", required = false) Integer size);

    /**
     * 查询规则集的发布引用分页及全量使用事实。
     *
     * @param actorAccountId
     *            Gateway 传入的操作者 Account ID
     * @param sessionId
     *            Gateway 传入的会话标识
     * @param ruleSetId
     *            整体规则根 ID，不存在或整体删除返回 404
     * @param page
     *            页码，缺省为 1，范围为 1–100000
     * @param size
     *            每页条数，缺省为 10，只允许 10、20、50、100、200
     * @param environmentCode
     *            可选发布环境 TEST 或 PRODUCTION，省略查询全部，空值或未知值返回 400
     * @param status
     *            可选发布生命周期状态，省略查询全部，空值或未知值返回 400
     * @param ruleSetVersionId
     *            可选精确规则版本 ID，必须属于当前根，否则返回 404
     * @return 同一快照中的全根 inUse 和筛选页；inUse 不受筛选及分页影响，引用按发布 ID、规则版本 ID 倒序
     */
    @GetExchange("/{ruleSetId}/release-references")
    RuleSetReleaseReferencesResponseBody releases(@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId, @PathVariable("ruleSetId") String ruleSetId,
        @RequestParam(value = "page", required = false) Integer page, @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "environmentCode", required = false) String environmentCode,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "ruleSetVersionId", required = false) String ruleSetVersionId);
}
