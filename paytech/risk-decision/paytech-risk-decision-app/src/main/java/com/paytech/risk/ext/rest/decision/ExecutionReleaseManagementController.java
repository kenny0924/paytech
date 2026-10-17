package com.paytech.risk.ext.rest.decision;

import static com.paytech.risk.service.convert.DecisionReleaseExceptionConverter.mapped;

import java.time.Instant;
import java.util.Arrays;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.risk.decision.api.ExecutionReleaseManagementApi;
import com.paytech.risk.decision.api.request.CreateExecutionReleaseRequestBody;
import com.paytech.risk.decision.api.request.StartShadowRunRequestBody;
import com.paytech.risk.decision.api.response.ExecutionReleaseListItemResponseBody;
import com.paytech.risk.decision.api.response.ExecutionReleaseResponseBody;
import com.paytech.risk.decision.api.response.ShadowRun;
import com.paytech.risk.decision.api.response.StaticCheck;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.release.ExecutionReleasePageView;
import com.paytech.risk.decision.domain.release.ExecutionReleaseView;
import com.paytech.risk.decision.service.processor.ActivateExecutionReleaseProcessor;
import com.paytech.risk.decision.service.processor.CreateExecutionReleaseProcessor;
import com.paytech.risk.decision.service.processor.GetExecutionReleaseProcessor;
import com.paytech.risk.decision.service.processor.ListExecutionReleasesProcessor;
import com.paytech.risk.decision.service.processor.StartShadowRunProcessor;
import com.paytech.risk.decision.service.processor.ValidateExecutionReleaseProcessor;
import com.paytech.risk.decision.service.processor.request.CreateExecutionReleaseRequest;
import com.paytech.risk.decision.service.processor.request.ExecutionReleaseCommandRequest;
import com.paytech.risk.decision.service.processor.request.ListExecutionReleasesRequest;
import com.paytech.risk.decision.service.processor.request.StartShadowRunRequest;
import com.paytech.foundation.web.request.OperationRequestContextParser;

import lombok.AllArgsConstructor;

/** 只解析可信上下文和协议字段，执行发布用例由 risk-decision 的 Processor 执行。 */
@RestController
@AllArgsConstructor
public class ExecutionReleaseManagementController implements ExecutionReleaseManagementApi {
    private final OperationRequestContextParser context;
    private final GetExecutionReleaseProcessor getProcessor;
    private final ListExecutionReleasesProcessor listProcessor;
    private final StartShadowRunProcessor shadowProcessor;
    private final CreateExecutionReleaseProcessor createProcessor;
    private final ValidateExecutionReleaseProcessor validateProcessor;
    private final ActivateExecutionReleaseProcessor activateProcessor;

    /** 创建执行发布。 */
    @Override
    public ExecutionReleaseResponseBody createExecution(String actorAccountId, String sessionId,
        CreateExecutionReleaseRequestBody requestBody) {
        var actor = context.parse(actorAccountId, sessionId);
        var request = new CreateExecutionReleaseRequest(actor.getActorAccountId(),
            context.parseRequiredUpperCode("pointCode", requestBody.getPointCode()),
            context.parsePositiveId("flowVersionId", requestBody.getFlowVersionId()));
        return toBody(mapped(() -> createProcessor.process(request)));
    }

    /** 分页查询执行发布；pointCode 必填，状态可选。 */
    @Override
    public PagingResponseBody<ExecutionReleaseListItemResponseBody> listExecutions(String actorAccountId,
        String sessionId, Integer page, Integer size, String pointCode, String status) {
        context.parse(actorAccountId, sessionId);
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        var request = new ListExecutionReleasesRequest(paging.page(), paging.size(),
            context.parseRequiredUpperCode("pointCode", pointCode),
            parseStatus(context.parseOptionalUpperCode("status", status)));
        ExecutionReleasePageView view = mapped(() -> listProcessor.process(request));
        return new PagingResponseBody<>(view.items().stream().map(ExecutionReleaseManagementController::toBody).toList(),
            view.page(), view.size(), view.total());
    }

    /** 查询执行发布。 */
    @Override
    public ExecutionReleaseResponseBody getExecution(String actorAccountId, String sessionId, String releaseId) {
        return toBody(mapped(() -> getProcessor.process(command(actorAccountId, sessionId, releaseId))));
    }

    /** 执行一轮静态校验。 */
    @Override
    public ExecutionReleaseResponseBody validateExecution(String actorAccountId, String sessionId, String releaseId) {
        return toBody(mapped(() -> validateProcessor.process(command(actorAccountId, sessionId, releaseId))));
    }

    /** 启动影子运行。 */
    @Override
    public ExecutionReleaseResponseBody startShadowRun(String actorAccountId, String sessionId, String releaseId,
        StartShadowRunRequestBody requestBody) {
        var actor = context.parse(actorAccountId, sessionId);
        var request = new StartShadowRunRequest(actor.getActorAccountId(), context.parsePositiveId("releaseId", releaseId),
            requestBody.getSampleTarget());
        return toBody(mapped(() -> shadowProcessor.process(request)));
    }

    /** 激活 TEST 执行发布。 */
    @Override
    public ExecutionReleaseResponseBody activateExecution(String actorAccountId, String sessionId, String releaseId) {
        return toBody(mapped(() -> activateProcessor.process(command(actorAccountId, sessionId, releaseId))));
    }

    /** 解析可信上下文与发布 ID。 */
    ExecutionReleaseCommandRequest command(String actorAccountId, String sessionId, String releaseId) {
        var actor = context.parse(actorAccountId, sessionId);
        return new ExecutionReleaseCommandRequest(actor.getActorAccountId(), context.parsePositiveId("releaseId", releaseId));
    }

    /** 状态筛选：空值不筛选，未知 Code 拒绝。 */
    static DecisionReleaseStatus parseStatus(String value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(DecisionReleaseStatus.values()).filter(status -> status.getCode().equals(value)).findFirst()
            .orElseThrow(() -> new InvalidParameterException("status 格式无效"));
    }

    /** 列表项转换；ID 与时间以字符串传输。 */
    static ExecutionReleaseListItemResponseBody toBody(ExecutionReleasePageView.Item item) {
        ExecutionReleaseListItemResponseBody body = new ExecutionReleaseListItemResponseBody();
        body.setReleaseId(Long.toString(item.releaseId()));
        body.setReleaseNo(item.releaseNo());
        body.setPointCode(item.pointCode());
        body.setEnvironment(item.environment());
        body.setStatus(item.status());
        body.setActive(item.active());
        body.setFlowId(Long.toString(item.flowId()));
        body.setFlowCode(item.flowCode());
        body.setFlowVersionId(Long.toString(item.flowVersionId()));
        body.setFlowVersionNo(item.flowVersionNo());
        body.setContentHash(item.contentHash());
        body.setCreatedBy(Long.toString(item.createdBy()));
        body.setCreateTime(text(item.createTime()));
        body.setActivatedBy(item.activatedBy() == null ? null : Long.toString(item.activatedBy()));
        body.setActivatedTime(text(item.activatedTime()));
        body.setSupersededTime(text(item.supersededTime()));
        return body;
    }

    /** 查询结果转换；ID 与时间以字符串传输。 */
    static ExecutionReleaseResponseBody toBody(ExecutionReleaseView view) {
        ExecutionReleaseResponseBody body = new ExecutionReleaseResponseBody();
        body.setReleaseId(Long.toString(view.releaseId()));
        body.setReleaseNo(view.releaseNo());
        body.setPointCode(view.pointCode());
        body.setFlowVersionId(Long.toString(view.flowVersionId()));
        body.setEnvironment(view.environment());
        body.setStatus(view.status());
        body.setVersion(view.version());
        body.setContentHash(view.contentHash());
        body.setActivatedTime(text(view.activatedTime()));
        body.setSupersededTime(text(view.supersededTime()));
        body.setChecks(view.checks().stream().map(check -> {
            StaticCheck item = new StaticCheck();
            item.setCheckCode(check.checkCode().getCode());
            item.setCheckStatus(check.checkStatus().getCode());
            item.setErrorCode(check.errorCode());
            item.setErrorSummary(check.errorSummary());
            return item;
        }).toList());
        body.setShadowRun(view.shadowRun() == null ? null : toBody(view.shadowRun()));
        return body;
    }

    /** 影子运行汇总转换。 */
    static ShadowRun toBody(ExecutionReleaseView.ShadowRunSummary summary) {
        ShadowRun run = new ShadowRun();
        run.setShadowRunId(Long.toString(summary.shadowRunId()));
        run.setBaselineReleaseId(Long.toString(summary.baselineReleaseId()));
        run.setStatus(summary.status());
        run.setSampleTarget(summary.sampleTarget());
        run.setSampleCount(summary.sampleCount());
        run.setAllowToReviewCount(summary.allowToReviewCount());
        run.setAllowToDenyCount(summary.allowToDenyCount());
        run.setDenyToAllowCount(summary.denyToAllowCount());
        run.setActionChangeCount(summary.actionChangeCount());
        run.setFeatureMissingCount(summary.featureMissingCount());
        run.setFailedCount(summary.failedCount());
        run.setTimedOutCount(summary.timedOutCount());
        run.setP95Millis(summary.p95Millis());
        run.setP99Millis(summary.p99Millis());
        run.setCheckStatus(summary.checkStatus());
        return run;
    }

    /** 可空时间转 ISO-8601。 */
    static String text(Instant time) {
        return time == null ? null : time.toString();
    }
}
