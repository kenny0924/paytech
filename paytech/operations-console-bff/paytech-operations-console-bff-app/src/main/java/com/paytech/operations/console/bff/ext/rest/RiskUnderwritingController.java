package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.RiskUnderwritingClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.riskunderwriting.CreateUnderwritingDraftPageRequestBody;
import com.paytech.operations.console.bff.service.model.riskunderwriting.LabeledUnderwritingResponseBody;
import com.paytech.operations.console.bff.service.model.riskunderwriting.LabeledUnderwritingSummaryBody;
import com.paytech.operations.console.bff.service.model.riskunderwriting.ReviseUnderwritingPageRequestBody;
import com.paytech.operations.console.bff.service.model.riskunderwriting.SaveUnderwritingDraftPageRequestBody;
import com.paytech.operations.console.bff.service.model.riskunderwriting.SubmitUnderwritingPageRequestBody;
import com.paytech.operations.console.bff.service.model.riskunderwriting.UnderwritingHistoryPageBody;
import com.paytech.operations.console.bff.service.model.riskunderwriting.UnderwritingPreviewPageRequestBody;
import com.paytech.operations.console.bff.service.model.riskunderwriting.UnderwritingWorkspaceResponseBody;
import com.paytech.operations.console.bff.service.processor.UnderwritingCommandProcessor;
import com.paytech.operations.console.bff.service.processor.UnderwritingDetailProcessor;
import com.paytech.operations.console.bff.service.processor.UnderwritingHistoryProcessor;
import com.paytech.operations.console.bff.service.processor.UnderwritingListProcessor;
import com.paytech.operations.console.bff.service.processor.UnderwritingPreviewProcessor;
import com.paytech.operations.console.bff.service.processor.UnderwritingWorkspaceProcessor;
import com.paytech.operations.console.bff.service.processor.request.UnderwritingCommandRequest;
import com.paytech.operations.console.bff.service.processor.request.UnderwritingListRequest;
import com.paytech.operations.console.bff.service.processor.request.UnderwritingPreviewRequest;
import com.paytech.operations.console.bff.service.processor.request.UnderwritingRecordRequest;
import com.paytech.operations.console.bff.service.processor.request.UnderwritingWorkspaceRequest;
import com.paytech.risk.decision.api.control.request.AutoUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.request.CreateUnderwritingDraftRequestBody;
import com.paytech.risk.decision.api.control.request.ReviewUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.request.ReviseUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.request.SaveUnderwritingDraftRequestBody;
import com.paytech.risk.decision.api.control.request.SubmitUnderwritingRequestBody;
import com.paytech.risk.decision.api.control.response.UnderwritingPreviewResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingStatisticsResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;

/**
 * 风控承保（REQ-03）的浏览器入口
 * <p>每个路由由 Gateway 绑定权限（读 RISK_UNDERWRITING_READ、维护 RISK_UNDERWRITING_MANAGE、复核 RISK_UNDERWRITING_APPROVE）并经
 * PLT-I02 实时授权。页面命令只发结论字段，输入快照由 BFF 重新从 Owner 读取后随命令转发给 Risk；统计为简单转发。
 */
@RestController
@AllArgsConstructor
@RequestMapping("/risk-underwriting/v1")
public class RiskUnderwritingController {
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final String CODE = "[A-Z][A-Z0-9_]{0,63}";

    private final RiskUnderwritingClient client;
    private final UnderwritingListProcessor listProcessor;
    private final UnderwritingDetailProcessor detailProcessor;
    private final UnderwritingCommandProcessor commandProcessor;
    private final UnderwritingHistoryProcessor historyProcessor;
    private final UnderwritingPreviewProcessor previewProcessor;
    private final UnderwritingWorkspaceProcessor workspaceProcessor;

    /** 承保版本分页，更新时间倒序。 */
    @GetMapping("/underwritings")
    public PagingResponseBody<LabeledUnderwritingSummaryBody> list(HttpServletRequest servletRequest, OperatorContext operator,
        @RequestParam(value = "status", required = false) @Pattern(regexp = CODE) String status,
        @RequestParam(value = "crr", required = false) @Pattern(regexp = CODE) String crr,
        @RequestParam(value = "decisionSource", required = false) @Pattern(regexp = CODE) String decisionSource,
        @RequestParam(value = "keyword", required = false) @Size(max = 100) String keyword,
        @RequestParam(value = "clientId", required = false) @Pattern(regexp = ID) String clientId,
        @RequestParam(value = "pendingReviewForMe", required = false) Boolean pendingReviewForMe,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest,
            Set.of("status", "crr", "decisionSource", "keyword", "clientId", "pendingReviewForMe", "page", "size"));
        return listProcessor.process(new UnderwritingListRequest(operator, status, crr, decisionSource, keyword, clientId,
            Boolean.TRUE.equals(pendingReviewForMe), PagingRequestBody.of(page, size)));
    }

    /** 承保版本详情。 */
    @GetMapping("/underwritings/{underwritingId}")
    public LabeledUnderwritingResponseBody detail(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("underwritingId") @Pattern(regexp = ID) String underwritingId) {
        requireQueryKeys(servletRequest, Set.of());
        return detailProcessor.process(new UnderwritingRecordRequest(operator, underwritingId));
    }

    /** 承保工作台：输入快照、来源、自动承保判定与估算敞口、当前承保。 */
    @GetMapping("/clients/{clientId}/workspace")
    public UnderwritingWorkspaceResponseBody workspace(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("clientId") @Pattern(regexp = ID) String clientId) {
        requireQueryKeys(servletRequest, Set.of());
        return workspaceProcessor.process(new UnderwritingWorkspaceRequest(operator, clientId));
    }

    /** Client 承保版本历史。 */
    @GetMapping("/clients/{clientId}/underwritings")
    public UnderwritingHistoryPageBody history(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("clientId") @Pattern(regexp = ID) String clientId) {
        requireQueryKeys(servletRequest, Set.of());
        return historyProcessor.process(new UnderwritingRecordRequest(operator, clientId));
    }

    /** 建承保草稿，成功 201。 */
    @PostMapping("/clients/{clientId}/underwritings")
    @ResponseStatus(HttpStatus.CREATED)
    public LabeledUnderwritingResponseBody create(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("clientId") @Pattern(regexp = ID) String clientId,
        @RequestBody @NotNull @Valid CreateUnderwritingDraftPageRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return commandProcessor.process(new UnderwritingCommandRequest(operator, clientId, null, true, input -> {
            CreateUnderwritingDraftRequestBody risk = new CreateUnderwritingDraftRequestBody();
            risk.setInput(input);
            risk.setDecision(body.getDecision());
            return client.create(operator, clientId, risk);
        }));
    }

    /** 保存承保草稿。 */
    @PutMapping("/underwritings/{underwritingId}")
    public LabeledUnderwritingResponseBody save(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("underwritingId") @Pattern(regexp = ID) String underwritingId,
        @RequestBody @NotNull @Valid SaveUnderwritingDraftPageRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return commandProcessor.process(new UnderwritingCommandRequest(operator, null, underwritingId, true, input -> {
            SaveUnderwritingDraftRequestBody risk = new SaveUnderwritingDraftRequestBody();
            risk.setExpectedVersion(body.getExpectedVersion());
            risk.setInput(input);
            risk.setDecision(body.getDecision());
            return client.save(operator, underwritingId, risk);
        }));
    }

    /** 提交承保：非 HIGH 即决定，HIGH 进入待复核。 */
    @PostMapping("/underwritings/{underwritingId}/submission")
    public LabeledUnderwritingResponseBody submit(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("underwritingId") @Pattern(regexp = ID) String underwritingId,
        @RequestBody @NotNull @Valid SubmitUnderwritingPageRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return commandProcessor.process(new UnderwritingCommandRequest(operator, null, underwritingId, true, input -> {
            SubmitUnderwritingRequestBody risk = new SubmitUnderwritingRequestBody();
            risk.setExpectedVersion(body.getExpectedVersion());
            risk.setInput(input);
            risk.setDecision(body.getDecision());
            risk.setOutcome(body.getOutcome());
            risk.setDeclineReason(body.getDeclineReason());
            return client.submit(operator, underwritingId, risk);
        }));
    }

    /** 复核 HIGH 风险承保（四眼）；页面 Body 原样转发。 */
    @PostMapping("/underwritings/{underwritingId}/review")
    public LabeledUnderwritingResponseBody review(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("underwritingId") @Pattern(regexp = ID) String underwritingId,
        @RequestBody @NotNull @Valid ReviewUnderwritingRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return commandProcessor.process(new UnderwritingCommandRequest(operator, null, underwritingId, false,
            input -> client.review(operator, underwritingId, body)));
    }

    /** 按自动承保规则生成 APPROVED，成功 201；页面不需要 Body。 */
    @PostMapping("/clients/{clientId}/auto-underwritings")
    @ResponseStatus(HttpStatus.CREATED)
    public LabeledUnderwritingResponseBody auto(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("clientId") @Pattern(regexp = ID) String clientId) {
        requireQueryKeys(servletRequest, Set.of());
        return commandProcessor.process(new UnderwritingCommandRequest(operator, clientId, null, true, input -> {
            AutoUnderwritingRequestBody risk = new AutoUnderwritingRequestBody();
            risk.setInput(input);
            return client.auto(operator, clientId, risk);
        }));
    }

    /** 基于有效 APPROVED 建修订草稿，成功 201。 */
    @PostMapping("/underwritings/{underwritingId}/revision")
    @ResponseStatus(HttpStatus.CREATED)
    public LabeledUnderwritingResponseBody revise(HttpServletRequest servletRequest, OperatorContext operator,
        @PathVariable("underwritingId") @Pattern(regexp = ID) String underwritingId,
        @RequestBody @NotNull @Valid ReviseUnderwritingPageRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return commandProcessor.process(new UnderwritingCommandRequest(operator, null, underwritingId, true, input -> {
            ReviseUnderwritingRequestBody risk = new ReviseUnderwritingRequestBody();
            risk.setExpectedVersion(body.getExpectedVersion());
            risk.setRevisionReason(body.getRevisionReason());
            risk.setInput(input);
            return client.revise(operator, underwritingId, risk);
        }));
    }

    /** 自动承保判定与估算敞口预览。 */
    @PostMapping("/previews")
    public UnderwritingPreviewResponseBody preview(HttpServletRequest servletRequest, OperatorContext operator,
        @RequestBody @NotNull @Valid UnderwritingPreviewPageRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return previewProcessor.process(new UnderwritingPreviewRequest(operator, body.getClientId(),
            body.getSuggestedSettlementDays()));
    }

    /** 列表顶部统计。 */
    @GetMapping("/underwriting-statistics")
    public UnderwritingStatisticsResponseBody statistics(HttpServletRequest servletRequest, OperatorContext operator) {
        requireQueryKeys(servletRequest, Set.of());
        return client.statistics(operator);
    }
}
