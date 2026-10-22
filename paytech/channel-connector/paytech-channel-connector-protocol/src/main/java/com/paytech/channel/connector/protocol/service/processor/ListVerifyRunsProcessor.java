package com.paytech.channel.connector.protocol.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyRun;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.verify.VerifyRunSummaryView;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyCaseRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyRunRepository;
import com.paytech.channel.connector.protocol.service.processor.request.ListVerifyRunsRequest;

import lombok.AllArgsConstructor;
import tools.jackson.databind.JsonNode;

/** V13 验证运行列表：按开始时间倒序，可按用例筛选；用例编码与名称取自运行时快照。 */
@Component
@AllArgsConstructor
public class ListVerifyRunsProcessor
    extends AbstractProcessor<ListVerifyRunsRequest, PagingResponseBody<VerifyRunSummaryView>> {

    private final VerifySupport verify;
    private final IntegrationChildSupport children;
    private final ExternalApiProcessorSupport support;
    private final ChannelVerifyCaseRepository cases;
    private final ChannelVerifyRunRepository runs;

    @Override
    protected PagingResponseBody<VerifyRunSummaryView> processRequest(ListVerifyRunsRequest request) {
        long channelId = children.channel(request.getIntegrationCode()).getChannelId();
        Long caseId = request.getCaseCode() == null ? null
            : support.requireFound(cases.byCode(channelId, request.getCaseCode())).getVerifyCaseId();
        var paging = request.getPaging();
        var items = runs.list(channelId, caseId, paging.offset(), paging.size()).stream().map(this::summary)
            .toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), runs.count(channelId, caseId));
    }

    VerifyRunSummaryView summary(ChannelVerifyRun run) {
        JsonNode snapshot = verify.tree(run.getCaseSnapshotJson());
        return new VerifyRunSummaryView(run.getVerifyRunId(), snapshot.path("caseCode").asString(),
            snapshot.path("caseName").asString(), run.getRunStatus(), run.getFailureSummary(), run.getStartedBy(),
            run.getStartTime(), run.getFinishTime());
    }
}
