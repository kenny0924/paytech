package com.paytech.risk.decision.api.control;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.decision.api.control.request.DecideClientTierChangeRequestBody;
import com.paytech.risk.decision.api.control.request.MigrateClientTiersRequestBody;
import com.paytech.risk.decision.api.control.request.RequestClientTierChangeRequestBody;
import com.paytech.risk.decision.api.control.request.WithdrawClientTierChangeRequestBody;
import com.paytech.risk.decision.api.control.response.ClientTierChangeListResponseBody;
import com.paytech.risk.decision.api.control.response.ClientTierChangeResponseBody;
import com.paytech.risk.decision.api.control.response.ClientTierDetailResponseBody;
import com.paytech.risk.decision.api.control.response.ClientTierSummaryBody;
import com.paytech.risk.decision.api.control.response.TierMigrationResponseBody;
import com.paytech.risk.decision.api.control.response.TierQuotaListResponseBody;
import com.paytech.risk.decision.api.control.response.TierStatisticsResponseBody;

import jakarta.validation.Valid;

/**
 * Client 限额档位的内部 HTTP 合同（REQ-04），由 operations-console-bff 调用
 * <li>权限由 Gateway 路由绑定：读 RISK_LIMIT_TIER_READ，申请与撤回 RISK_LIMIT_TIER_REQUEST，审批 RISK_LIMIT_TIER_APPROVE，
 * 高级审批 RISK_LIMIT_TIER_APPROVE_ELEVATED；risk-decision 只解析操作人，按申请冻结的审批级别校验走的是哪个接口，并校验申请人不能审批。</li>
 * <li>档位表为权威，四张 TIER 名单成员在同一事务投影；HTTP 请求幂等由 Gateway 负责，每个 Client 至多一个待审批申请由唯一槽位保证。</li>
 * <li>错误：字段 422 VALIDATION_ERROR；404 / 409 见 RiskLimitTierErrorCode 与 CONCURRENT_REQUEST。</li>
 */
@HttpExchange("/risk-limit-tiers/v1")
public interface ClientLimitTierApi {

    /**
     * 分页查询 Client 档位（L1）
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID
     * @param sessionId Gateway 传入的可信会话标识
     * @param tier 可选档位：VERIFICATION、OBSERVATION、STANDARD、RESTRICTED；空为全部
     * @param pendingApproval 可选；true 只列有待审批申请的 Client
     * @param observationDue 可选；true 只列 OBSERVATION 且已过天数达到目标天数的 Client
     * @param keyword 可选关键字：Client 名称包含，或 Client 编号 / ID 相等
     * @param page 页码，缺省 1
     * @param size 每页条数，缺省 10，只允许 10、20、50、100、200
     * @return 按档位行更新时间倒序的分页；放行笔数只对本页 OBSERVATION 行计算
     */
    @GetExchange("/client-tiers")
    PagingResponseBody<ClientTierSummaryBody> listClientTiers(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam(value = "tier", required = false) String tier,
        @RequestParam(value = "pendingApproval", required = false) Boolean pendingApproval,
        @RequestParam(value = "observationDue", required = false) Boolean observationDue,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 读取 Client 当前档位详情（L2）
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID
     * @param sessionId Gateway 传入的可信会话标识
     * @param clientId Client ID
     * @return 档位、观察期进度、本档位按币种额度与已用、待审批申请；无档位时 tier 为空
     */
    @GetExchange("/clients/{clientId}/tier")
    ClientTierDetailResponseBody getClientTier(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("clientId") String clientId);

    /**
     * 读取 Client 的档位变更历史（L3）
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID
     * @param sessionId Gateway 传入的可信会话标识
     * @param clientId Client ID
     * @return 申请时间倒序，最多 100 条
     */
    @GetExchange("/clients/{clientId}/tier-changes")
    ClientTierChangeListResponseBody listClientTierChanges(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("clientId") String clientId);

    /**
     * 提交档位变更申请（L4）
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID，记录为申请人
     * @param sessionId Gateway 传入的可信会话标识
     * @param clientId Client ID
     * @param body 目标档位、理由、观察目标与上线复核清单
     * @return 201 新申请；无档位 404，不允许的迁移 / 已有待审批 / 版本不符 / 清单未满足 409
     */
    @PostExchange("/clients/{clientId}/tier-changes")
    ClientTierChangeResponseBody requestClientTierChange(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("clientId") String clientId,
        @Valid @RequestBody RequestClientTierChangeRequestBody body);

    /**
     * 申请人撤回待审批申请（L5）
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID，须是申请人
     * @param sessionId Gateway 传入的可信会话标识
     * @param changeId 申请 ID
     * @param body 期望版本
     * @return 撤回后的申请
     */
    @PostExchange("/tier-changes/{changeId}/withdrawal")
    ClientTierChangeResponseBody withdrawClientTierChange(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("changeId") String changeId,
        @Valid @RequestBody WithdrawClientTierChangeRequestBody body);

    /**
     * 普通审批通过（L6），只处理审批级别 STANDARD（CRR=LOW）的申请
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID，不能是申请人
     * @param sessionId Gateway 传入的可信会话标识
     * @param changeId 申请 ID
     * @param body 期望版本与可选意见
     * @return 批准后的申请；档位与名单成员同一事务生效。ELEVATED 申请返回 409 RISK_LIMIT_TIER_ELEVATION_REQUIRED
     */
    @PostExchange("/tier-changes/{changeId}/approval")
    ClientTierChangeResponseBody approveClientTierChange(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("changeId") String changeId,
        @Valid @RequestBody DecideClientTierChangeRequestBody body);

    /**
     * 高级审批通过（L7），可处理任意审批级别的申请
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID，不能是申请人
     * @param sessionId Gateway 传入的可信会话标识
     * @param changeId 申请 ID
     * @param body 期望版本与可选意见
     * @return 批准后的申请；档位与名单成员同一事务生效
     */
    @PostExchange("/tier-changes/{changeId}/elevated-approval")
    ClientTierChangeResponseBody approveClientTierChangeElevated(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("changeId") String changeId,
        @Valid @RequestBody DecideClientTierChangeRequestBody body);

    /**
     * 普通驳回（L8），只处理审批级别 STANDARD 的申请
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID，不能是申请人
     * @param sessionId Gateway 传入的可信会话标识
     * @param changeId 申请 ID
     * @param body 期望版本与必填意见
     * @return 驳回后的申请；ELEVATED 申请返回 409 RISK_LIMIT_TIER_ELEVATION_REQUIRED
     */
    @PostExchange("/tier-changes/{changeId}/rejection")
    ClientTierChangeResponseBody rejectClientTierChange(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("changeId") String changeId,
        @Valid @RequestBody DecideClientTierChangeRequestBody body);

    /**
     * 高级驳回（L9），可处理任意审批级别的申请
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID，不能是申请人
     * @param sessionId Gateway 传入的可信会话标识
     * @param changeId 申请 ID
     * @param body 期望版本与必填意见
     * @return 驳回后的申请
     */
    @PostExchange("/tier-changes/{changeId}/elevated-rejection")
    ClientTierChangeResponseBody rejectClientTierChangeElevated(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("changeId") String changeId,
        @Valid @RequestBody DecideClientTierChangeRequestBody body);

    /**
     * 读取四档 × 币种额度（L10）
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID
     * @param sessionId Gateway 传入的可信会话标识
     * @return 按命名约定解析的额度与配置问题
     */
    @GetExchange("/tier-quotas")
    TierQuotaListResponseBody listTierQuotas(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId);

    /**
     * 读取档位列表顶部统计（L11）
     *
     * @param actorAccountId Gateway 传入的可信操作者 Account ID
     * @param sessionId Gateway 传入的可信会话标识
     * @return 各档位数量、待审批数与观察期到期数
     */
    @GetExchange("/tier-statistics")
    TierStatisticsResponseBody getTierStatistics(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId);

    /**
     * 存量 Client 迁移为 STANDARD（L12，内部接口，不注册 Gateway 路由）
     * <li>只接受白名单调用方（配置 risk.limit-tier.migration-callers，默认空即关闭）；迁移完成后关闭白名单。</li>
     *
     * @param callerService 私网调用方服务名
     * @param body 迁移理由与 Client 列表
     * @return 每个 Client 的结果：CREATED 或 SKIPPED_EXISTING；调用方不在白名单返回 403 RISK_LIMIT_TIER_MIGRATION_FORBIDDEN
     */
    @PostExchange("/internal/tier-migrations")
    TierMigrationResponseBody migrateClientTiers(
        @RequestHeader(value = "X-Caller-Service", required = false) String callerService,
        @Valid @RequestBody MigrateClientTiersRequestBody body);
}
