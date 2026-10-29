package com.paytech.risk.ext.rest.control;

import static com.paytech.risk.service.convert.UnderwritingExceptionConverter.mapped;

import java.time.Clock;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.foundation.web.request.OperationRequestContextParser;
import com.paytech.risk.control.core.enums.UnderwritingDecisionSource;
import com.paytech.risk.control.core.enums.UnderwritingRiskLevel;
import com.paytech.risk.control.core.enums.UnderwritingStatus;
import com.paytech.risk.control.domain.underwriting.UnderwritingException;
import com.paytech.risk.control.domain.underwriting.UnderwritingException.Reason;
import com.paytech.risk.control.domain.underwriting.UnderwritingListFilter;
import com.paytech.risk.control.service.UnderwritingService;
import com.paytech.risk.control.service.processor.AutoUnderwritingProcessor;
import com.paytech.risk.control.service.processor.CreateUnderwritingDraftProcessor;
import com.paytech.risk.control.service.processor.ReviewUnderwritingProcessor;
import com.paytech.risk.control.service.processor.ReviseUnderwritingProcessor;
import com.paytech.risk.control.service.processor.SaveUnderwritingDraftProcessor;
import com.paytech.risk.control.service.processor.SubmitUnderwritingProcessor;
import com.paytech.risk.control.service.processor.request.AutoUnderwritingRequest;
import com.paytech.risk.control.service.processor.request.CreateUnderwritingDraftRequest;
import com.paytech.risk.control.service.processor.request.ReviewUnderwritingRequest;
import com.paytech.risk.control.service.processor.request.ReviseUnderwritingRequest;
import com.paytech.risk.control.service.processor.request.SaveUnderwritingDraftRequest;
import com.paytech.risk.control.service.processor.request.SubmitUnderwritingRequest;
import com.paytech.risk.decision.api.control.ClientUnderwritingApi;
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

import lombok.AllArgsConstructor;

/** 只解析可信上下文和协议字段；写操作由 risk-control 的承保 Processor 执行，查询与预览委托承保 Service。 */
@RestController
@AllArgsConstructor
public class ClientUnderwritingController implements ClientUnderwritingApi {
    /** 关键字最长字符数。 */
    static final int KEYWORD_MAX_LENGTH = 100;

    private final Clock clock;
    private final UnderwritingService service;
    private final OperationRequestContextParser context;
    private final AutoUnderwritingProcessor autoProcessor;
    private final ReviewUnderwritingProcessor reviewProcessor;
    private final ReviseUnderwritingProcessor reviseProcessor;
    private final SubmitUnderwritingProcessor submitProcessor;
    private final SaveUnderwritingDraftProcessor saveProcessor;
    private final CreateUnderwritingDraftProcessor createProcessor;

    /** 分页查询承保版本。 */
    @Override
    public PagingResponseBody<UnderwritingSummaryBody> listUnderwritings(String actorAccountId, String sessionId,
        String status, String crr, String decisionSource, String keyword, String clientId, String pendingReviewFor,
        Integer page, Integer size) {
        context.parse(actorAccountId, sessionId);
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        UnderwritingListFilter filter = filter(status, crr, decisionSource, keyword, clientId, pendingReviewFor);
        var result = service.findPage(filter, paging.size(), paging.offset());
        return new PagingResponseBody<>(result.items().stream().map(UnderwritingWireConverter::summary).toList(),
            paging.page(), paging.size(), result.total());
    }

    /** 读取单个承保版本。 */
    @Override
    public UnderwritingResponseBody getUnderwriting(String actorAccountId, String sessionId, String underwritingId) {
        context.parse(actorAccountId, sessionId);
        long id = context.parsePositiveId("underwritingId", underwritingId);
        return mapped(() -> UnderwritingWireConverter.response(service.findView(id)
            .orElseThrow(() -> new UnderwritingException(Reason.NOT_FOUND, "承保记录不存在: " + id))));
    }

    /** 读取 Client 当前的承保。 */
    @Override
    public ClientUnderwritingCurrentResponseBody getCurrentUnderwriting(String actorAccountId, String sessionId,
        String clientId) {
        context.parse(actorAccountId, sessionId);
        return UnderwritingWireConverter.current(service.findCurrent(context.parsePositiveId("clientId", clientId)));
    }

    /** 读取 Client 的承保版本历史。 */
    @Override
    public UnderwritingHistoryResponseBody getUnderwritingHistory(String actorAccountId, String sessionId,
        String clientId) {
        context.parse(actorAccountId, sessionId);
        return UnderwritingWireConverter.history(service.findHistory(context.parsePositiveId("clientId", clientId)));
    }

    /** 建承保草稿，成功返回 201。 */
    @Override
    @ResponseStatus(HttpStatus.CREATED)
    public UnderwritingResponseBody createUnderwritingDraft(String actorAccountId, String sessionId, String clientId,
        CreateUnderwritingDraftRequestBody body) {
        var actor = context.parse(actorAccountId, sessionId);
        var request = new CreateUnderwritingDraftRequest(actor.getActorAccountId(), context.parsePositiveId("clientId", clientId),
            UnderwritingWireConverter.input(body.getInput()), UnderwritingWireConverter.terms(body.getDecision()));
        return UnderwritingWireConverter.response(mapped(() -> createProcessor.process(request)));
    }

    /** 保存承保草稿。 */
    @Override
    public UnderwritingResponseBody saveUnderwritingDraft(String actorAccountId, String sessionId, String underwritingId,
        SaveUnderwritingDraftRequestBody body) {
        var actor = context.parse(actorAccountId, sessionId);
        var request = new SaveUnderwritingDraftRequest(actor.getActorAccountId(),
            context.parsePositiveId("underwritingId", underwritingId), body.getExpectedVersion(),
            UnderwritingWireConverter.input(body.getInput()), UnderwritingWireConverter.terms(body.getDecision()));
        return UnderwritingWireConverter.response(mapped(() -> saveProcessor.process(request)));
    }

    /** 提交承保草稿。 */
    @Override
    public UnderwritingResponseBody submitUnderwriting(String actorAccountId, String sessionId, String underwritingId,
        SubmitUnderwritingRequestBody body) {
        var actor = context.parse(actorAccountId, sessionId);
        var request = new SubmitUnderwritingRequest(actor.getActorAccountId(),
            context.parsePositiveId("underwritingId", underwritingId), body.getExpectedVersion(),
            UnderwritingWireConverter.input(body.getInput()), UnderwritingWireConverter.terms(body.getDecision()),
            UnderwritingWireConverter.outcome(body.getOutcome(), "outcome"), body.getDeclineReason());
        return UnderwritingWireConverter.response(mapped(() -> submitProcessor.process(request)));
    }

    /** 复核 HIGH 风险承保。 */
    @Override
    public UnderwritingResponseBody reviewUnderwriting(String actorAccountId, String sessionId, String underwritingId,
        ReviewUnderwritingRequestBody body) {
        var actor = context.parse(actorAccountId, sessionId);
        var request = new ReviewUnderwritingRequest(actor.getActorAccountId(),
            context.parsePositiveId("underwritingId", underwritingId), body.getExpectedVersion(),
            UnderwritingWireConverter.outcome(body.getDecision(), "decision"), body.getComment());
        return UnderwritingWireConverter.response(mapped(() -> reviewProcessor.process(request)));
    }

    /** 按自动承保规则生成 APPROVED，成功返回 201。 */
    @Override
    @ResponseStatus(HttpStatus.CREATED)
    public UnderwritingResponseBody autoUnderwrite(String actorAccountId, String sessionId, String clientId,
        AutoUnderwritingRequestBody body) {
        var actor = context.parse(actorAccountId, sessionId);
        var request = new AutoUnderwritingRequest(actor.getActorAccountId(), context.parsePositiveId("clientId", clientId),
            UnderwritingWireConverter.input(body.getInput()));
        return UnderwritingWireConverter.response(mapped(() -> autoProcessor.process(request)));
    }

    /** 基于有效 APPROVED 建修订草稿，成功返回 201。 */
    @Override
    @ResponseStatus(HttpStatus.CREATED)
    public UnderwritingResponseBody reviseUnderwriting(String actorAccountId, String sessionId, String underwritingId,
        ReviseUnderwritingRequestBody body) {
        var actor = context.parse(actorAccountId, sessionId);
        var request = new ReviseUnderwritingRequest(actor.getActorAccountId(),
            context.parsePositiveId("underwritingId", underwritingId), body.getExpectedVersion(), body.getRevisionReason(),
            UnderwritingWireConverter.input(body.getInput()));
        return UnderwritingWireConverter.response(mapped(() -> reviseProcessor.process(request)));
    }

    /** 自动承保判定与估算敞口预览。 */
    @Override
    public UnderwritingPreviewResponseBody previewUnderwriting(String actorAccountId, String sessionId,
        UnderwritingPreviewRequestBody body) {
        context.parse(actorAccountId, sessionId);
        return UnderwritingWireConverter.preview(service.preview(UnderwritingWireConverter.input(body.getInput()),
            body.getSuggestedSettlementDays()));
    }

    /** 承保列表顶部统计。 */
    @Override
    public UnderwritingStatisticsResponseBody getUnderwritingStatistics(String actorAccountId, String sessionId) {
        context.parse(actorAccountId, sessionId);
        return UnderwritingWireConverter.statistics(service.statistics(clock.instant()));
    }

    /** 解析列表筛选；未知 Code、非法 ID 与超长关键字为参数错误。 */
    UnderwritingListFilter filter(String status, String crr, String decisionSource, String keyword, String clientId,
        String pendingReviewFor) {
        String text = keyword == null || keyword.isBlank() ? null : keyword.strip();
        if (text != null && text.length() > KEYWORD_MAX_LENGTH) {
            throw new InvalidParameterException("keyword 最长 " + KEYWORD_MAX_LENGTH + " 字符");
        }
        return new UnderwritingListFilter(
            UnderwritingWireConverter.code(UnderwritingStatus.values(), context.parseOptionalUpperCode("status", status), "status"),
            UnderwritingWireConverter.code(UnderwritingRiskLevel.values(), context.parseOptionalUpperCode("crr", crr), "crr"),
            UnderwritingWireConverter.code(UnderwritingDecisionSource.values(),
                context.parseOptionalUpperCode("decisionSource", decisionSource), "decisionSource"),
            text, text != null && text.matches("[1-9][0-9]{0,17}") ? Long.valueOf(text) : null,
            clientId == null ? null : context.parsePositiveId("clientId", clientId),
            pendingReviewFor == null ? null : context.parsePositiveId("pendingReviewFor", pendingReviewFor));
    }
}
