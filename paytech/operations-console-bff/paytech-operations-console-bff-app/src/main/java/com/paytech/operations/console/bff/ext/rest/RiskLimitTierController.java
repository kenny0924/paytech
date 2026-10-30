package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.RiskLimitTierClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.risklimittier.LabeledTierChangeBody;
import com.paytech.operations.console.bff.service.model.risklimittier.LimitTierHistoryPageBody;
import com.paytech.operations.console.bff.service.model.risklimittier.LimitTierWorkspaceResponseBody;
import com.paytech.operations.console.bff.service.model.risklimittier.RequestTierChangePageRequestBody;
import com.paytech.operations.console.bff.service.processor.LimitTierChangeCommandProcessor;
import com.paytech.operations.console.bff.service.processor.LimitTierHistoryProcessor;
import com.paytech.operations.console.bff.service.processor.LimitTierListProcessor;
import com.paytech.operations.console.bff.service.processor.LimitTierRequestProcessor;
import com.paytech.operations.console.bff.service.processor.LimitTierWorkspaceProcessor;
import com.paytech.operations.console.bff.service.processor.request.LimitTierChangeCommandRequest;
import com.paytech.operations.console.bff.service.processor.request.LimitTierChangeRequest;
import com.paytech.operations.console.bff.service.processor.request.LimitTierClientRequest;
import com.paytech.operations.console.bff.service.processor.request.LimitTierListRequest;
import com.paytech.risk.decision.api.control.request.DecideClientTierChangeRequestBody;
import com.paytech.risk.decision.api.control.request.WithdrawClientTierChangeRequestBody;
import com.paytech.risk.decision.api.control.response.ClientTierSummaryBody;
import com.paytech.risk.decision.api.control.response.TierQuotaListResponseBody;
import com.paytech.risk.decision.api.control.response.TierStatisticsResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;

/**
 * 风控限额档位（REQ-04）的浏览器入口，11 个路由
 * <p>每个路由由 Gateway 绑定权限（读 RISK_LIMIT_TIER_READ、申请与撤回 RISK_LIMIT_TIER_REQUEST、审批 RISK_LIMIT_TIER_APPROVE、
 * 高级审批 RISK_LIMIT_TIER_APPROVE_ELEVATED）并经 PLT-I02 实时授权。工作台为聚合；申请由 BFF 重新读取复核清单系统项后转发；
 * 其他为同路径转发（列表、历史与命令回执补操作人显示名）。存量迁移 L12 不对浏览器开放。
 */
@RestController
@AllArgsConstructor
@RequestMapping("/risk-limit-tiers/v1")
public class RiskLimitTierController {
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final String TIER = "VERIFICATION|OBSERVATION|STANDARD|RESTRICTED";

    private final RiskLimitTierClient client;
    private final LimitTierListProcessor listProcessor;
    private final LimitTierHistoryProcessor historyProcessor;
    private final LimitTierRequestProcessor requestProcessor;
    private final LimitTierWorkspaceProcessor workspaceProcessor;
    private final LimitTierChangeCommandProcessor commandProcessor;

    /** 档位分页，更新时间倒序。 */
    @GetMapping("/client-tiers")
    public PagingResponseBody<ClientTierSummaryBody> list(HttpServletRequest servletRequest, OperatorContext operator,
        @RequestParam(value = "tier", required = false) @Pattern(regexp = TIER) String tier,
        @RequestParam(value = "pendingApproval", required = false) Boolean pendingApproval,
        @RequestParam(value = "observationDue", required = false) Boolean observationDue,
        @RequestParam(value = "keyword", required = false) @Size(max = 100) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("tier", "pendingApproval", "observationDue", "keyword", "page", "size"));
        return listProcessor.process(new LimitTierListRequest(operator, tier, pendingApproval, observationDue, keyword,
            PagingRequestBody.of(page, size)));
    }

    /** 列表顶部统计。 */
    @GetMapping("/tier-statistics")
    public TierStatisticsResponseBody statistics(HttpServletRequest servletRequest, OperatorContext operator) {
        requireQueryKeys(servletRequest, Set.of());
        return client.statistics(operator);
    }

    /** 四档 × 币种额度。 */
    @GetMapping("/tier-quotas")
    public TierQuotaListResponseBody quotas(HttpServletRequest servletRequest, OperatorContext operator) {
        requireQueryKeys(servletRequest, Set.of());
        return client.quotas(operator);
    }

    /** Client 档位工作台：档位详情、历史、有效承保与复核清单系统项。 */
    @GetMapping("/clients/{clientId}/workspace")
    public LimitTierWorkspaceResponseBody workspace(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("clientId") @Pattern(regexp = ID) String clientId) {
        requireQueryKeys(servletRequest, Set.of());
        return workspaceProcessor.process(new LimitTierClientRequest(operator, clientId));
    }

    /** Client 档位变更历史。 */
    @GetMapping("/clients/{clientId}/tier-changes")
    public LimitTierHistoryPageBody history(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("clientId") @Pattern(regexp = ID) String clientId) {
        requireQueryKeys(servletRequest, Set.of());
        return historyProcessor.process(new LimitTierClientRequest(operator, clientId));
    }

    /** 提交档位变更申请，成功 201。 */
    @PostMapping("/clients/{clientId}/tier-changes")
    @ResponseStatus(HttpStatus.CREATED)
    public LabeledTierChangeBody request(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("clientId") @Pattern(regexp = ID) String clientId,
        @RequestBody @NotNull @Valid RequestTierChangePageRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return requestProcessor.process(new LimitTierChangeRequest(operator, clientId, body));
    }

    /** 申请人撤回。 */
    @PostMapping("/tier-changes/{changeId}/withdrawal")
    public LabeledTierChangeBody withdraw(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("changeId") @Pattern(regexp = ID) String changeId,
        @RequestBody @NotNull @Valid WithdrawClientTierChangeRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return commandProcessor.process(new LimitTierChangeCommandRequest(() -> client.withdraw(operator, changeId, body)));
    }

    /** 普通审批通过（CRR LOW 的申请）。 */
    @PostMapping("/tier-changes/{changeId}/approval")
    public LabeledTierChangeBody approve(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("changeId") @Pattern(regexp = ID) String changeId,
        @RequestBody @NotNull @Valid DecideClientTierChangeRequestBody body) {
        return decide(servletRequest, operator, changeId, true, false, body);
    }

    /** 高级审批通过。 */
    @PostMapping("/tier-changes/{changeId}/elevated-approval")
    public LabeledTierChangeBody approveElevated(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("changeId") @Pattern(regexp = ID) String changeId,
        @RequestBody @NotNull @Valid DecideClientTierChangeRequestBody body) {
        return decide(servletRequest, operator, changeId, true, true, body);
    }

    /** 普通驳回。 */
    @PostMapping("/tier-changes/{changeId}/rejection")
    public LabeledTierChangeBody reject(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("changeId") @Pattern(regexp = ID) String changeId,
        @RequestBody @NotNull @Valid DecideClientTierChangeRequestBody body) {
        return decide(servletRequest, operator, changeId, false, false, body);
    }

    /** 高级驳回。 */
    @PostMapping("/tier-changes/{changeId}/elevated-rejection")
    public LabeledTierChangeBody rejectElevated(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("changeId") @Pattern(regexp = ID) String changeId,
        @RequestBody @NotNull @Valid DecideClientTierChangeRequestBody body) {
        return decide(servletRequest, operator, changeId, false, true, body);
    }

    /** 审批与驳回的共同转发：页面 Body 原样，按路由选择 Risk 接口。 */
    LabeledTierChangeBody decide(HttpServletRequest servletRequest, OperatorContext operator, String changeId, boolean approve,
        boolean elevated, DecideClientTierChangeRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return commandProcessor.process(new LimitTierChangeCommandRequest(
            () -> client.decide(operator, changeId, approve, elevated, body)));
    }
}
