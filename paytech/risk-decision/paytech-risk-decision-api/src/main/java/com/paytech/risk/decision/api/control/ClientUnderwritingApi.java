package com.paytech.risk.decision.api.control;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.decision.api.control.request.AutoUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.request.CreateUnderwritingDraftRequestBody;
import com.paytech.risk.decision.api.control.request.ReviewUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.request.ReviseUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.request.SaveUnderwritingDraftRequestBody;
import com.paytech.risk.decision.api.control.request.SubmitUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.request.UnderwritingPreviewRequestBody;
import com.paytech.risk.decision.api.control.response.ClientUnderwritingCurrentResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingHistoryResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingPreviewResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingStatisticsResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingSummaryBody;

import jakarta.validation.Valid;

/**
 * Client 承保记录的内部 HTTP 合同（REQ-03），由 operations-console-bff 调用
 * <li>权限由 Gateway 路由绑定：读 RISK_UNDERWRITING_READ，维护 RISK_UNDERWRITING_MANAGE，复核 RISK_UNDERWRITING_APPROVE；
 * risk-decision 只解析操作人，复核人不能是提交人由本服务校验。</li>
 * <li>输入快照由 BFF 从 Owner 读取后随命令带入；HTTP 请求幂等由 Gateway 负责，进行中 / 有效结论唯一由版本行槽位保证。</li>
 * <li>错误：字段 422 VALIDATION_ERROR；404 RISK_UNDERWRITING_NOT_FOUND；409 见 RiskUnderwritingErrorCode 与 CONCURRENT_REQUEST。</li>
 */
@HttpExchange("/risk-underwriting/v1")
public interface ClientUnderwritingApi {

    /**
     * 分页查询承保版本
     * <li>不传 status（"全部"）时每个 Client 只返回最新版本（version_no 最大）；传 status 时返回该状态的全部版本，
     * 其余筛选在此基础上叠加，total 按同一口径计数。</li>
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID
     * @param sessionId Gateway 传入的可信会话标识
     * @param status 可选状态：DRAFT、SUBMITTED、APPROVED、DECLINED、SUPERSEDED
     * @param crr 可选 CRR：LOW、MEDIUM、HIGH
     * @param decisionSource 可选结论来源：MANUAL、AUTO_RULE
     * @param keyword 可选关键字：Client 名称包含，或 Client 编号 / ID 相等
     * @param clientId 可选 Client ID
     * @param pendingReviewFor 可选账号 ID：只列 SUBMITTED 且提交人不是该账号的版本
     * @param page 页码，缺省 1
     * @param size 每页条数，缺省 10，只允许 10、20、50、100、200
     * @return 按更新时间倒序的分页
     */
    @GetExchange("/underwritings")
    PagingResponseBody<UnderwritingSummaryBody> listUnderwritings(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "crr", required = false) String crr,
        @RequestParam(value = "decisionSource", required = false) String decisionSource,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "clientId", required = false) String clientId,
        @RequestParam(value = "pendingReviewFor", required = false) String pendingReviewFor,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 读取单个承保版本
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID
     * @param sessionId Gateway 传入的可信会话标识
     * @param underwritingId 承保版本 ID
     * @return 版本详情；不存在返回 404
     */
    @GetExchange("/underwritings/{underwritingId}")
    UnderwritingResponseBody getUnderwriting(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("underwritingId") String underwritingId);

    /**
     * 读取 Client 当前的承保（供 BFF 工作台聚合）
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID
     * @param sessionId Gateway 传入的可信会话标识
     * @param clientId Client ID
     * @return 有效、进行中与最新版本，均可为空
     */
    @GetExchange("/clients/{clientId}/underwriting")
    ClientUnderwritingCurrentResponseBody getCurrentUnderwriting(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("clientId") String clientId);

    /**
     * 读取 Client 的承保版本历史
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID
     * @param sessionId Gateway 传入的可信会话标识
     * @param clientId Client ID
     * @return 版本号倒序，最多 100 个
     */
    @GetExchange("/clients/{clientId}/underwritings")
    UnderwritingHistoryResponseBody getUnderwritingHistory(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("clientId") String clientId);

    /**
     * 建承保草稿
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID，记录为创建人
     * @param sessionId Gateway 传入的可信会话标识
     * @param clientId Client ID
     * @param body 输入快照与可选结论
     * @return 201 新草稿；已有进行中承保返回 409 RISK_UNDERWRITING_OPEN_EXISTS
     */
    @PostExchange("/clients/{clientId}/underwritings")
    UnderwritingResponseBody createUnderwritingDraft(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("clientId") String clientId,
        @Valid @RequestBody CreateUnderwritingDraftRequestBody body);

    /**
     * 保存承保草稿
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID
     * @param sessionId Gateway 传入的可信会话标识
     * @param underwritingId 草稿版本 ID
     * @param body 期望版本、输入快照与结论
     * @return 保存后的草稿；非草稿 409 STATE_INVALID，版本不符 409 VERSION_CONFLICT
     */
    @PutExchange("/underwritings/{underwritingId}")
    UnderwritingResponseBody saveUnderwritingDraft(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("underwritingId") String underwritingId,
        @Valid @RequestBody SaveUnderwritingDraftRequestBody body);

    /**
     * 提交承保草稿：冻结输入快照
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID，记录为提交人
     * @param sessionId Gateway 传入的可信会话标识
     * @param underwritingId 草稿版本 ID
     * @param body 期望版本、输入快照、结论与提交结论
     * @return 非 HIGH 为 APPROVED / DECLINED，HIGH 为 SUBMITTED；HIGH 提交拒绝返回 409 DECLINE_REQUIRES_REVIEW
     */
    @PostExchange("/underwritings/{underwritingId}/submission")
    UnderwritingResponseBody submitUnderwriting(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("underwritingId") String underwritingId,
        @Valid @RequestBody SubmitUnderwritingRequestBody body);

    /**
     * 复核 HIGH 风险承保（四眼）
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID，记录为决定人
     * @param sessionId Gateway 传入的可信会话标识
     * @param underwritingId 待复核版本 ID
     * @param body 期望版本、复核结论与意见
     * @return APPROVED 或 DECLINED；复核人是提交人返回 409 RISK_UNDERWRITING_SELF_REVIEW
     */
    @PostExchange("/underwritings/{underwritingId}/review")
    UnderwritingResponseBody reviewUnderwriting(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("underwritingId") String underwritingId,
        @Valid @RequestBody ReviewUnderwritingRequestBody body);

    /**
     * 按 ACTIVE 自动承保规则生成 APPROVED
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID，记录为触发人
     * @param sessionId Gateway 传入的可信会话标识
     * @param clientId Client ID
     * @param body 输入快照
     * @return 201 AUTO_RULE 的 APPROVED 版本；不满足返回 409 RISK_UNDERWRITING_AUTO_NOT_ELIGIBLE
     */
    @PostExchange("/clients/{clientId}/auto-underwritings")
    UnderwritingResponseBody autoUnderwrite(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("clientId") String clientId,
        @Valid @RequestBody AutoUnderwritingRequestBody body);

    /**
     * 基于有效 APPROVED 建修订草稿
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID，记录为创建人
     * @param sessionId Gateway 传入的可信会话标识
     * @param underwritingId 当前有效版本 ID
     * @param body 有效版本的期望版本、修订原因与最新输入
     * @return 201 新草稿；非有效版本 409 STATE_INVALID
     */
    @PostExchange("/underwritings/{underwritingId}/revision")
    UnderwritingResponseBody reviseUnderwriting(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("underwritingId") String underwritingId,
        @Valid @RequestBody ReviseUnderwritingRequestBody body);

    /**
     * 自动承保判定与估算敞口预览（纯计算，不写库）
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID
     * @param sessionId Gateway 传入的可信会话标识
     * @param body 输入快照与可选 T+N
     * @return 规则版本、各项检查、是否可自动承保与估算敞口
     */
    @PostExchange("/previews")
    UnderwritingPreviewResponseBody previewUnderwriting(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody UnderwritingPreviewRequestBody body);

    /**
     * 承保列表顶部统计
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID
     * @param sessionId Gateway 传入的可信会话标识
     * @return 生效中、草稿、待复核、本月已批准、本月已拒绝的数量
     */
    @GetExchange("/underwriting-statistics")
    UnderwritingStatisticsResponseBody getUnderwritingStatistics(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId);
}
