package com.paytech.channel.connector.protocol.service.processor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.channel.connector.api.enums.VerifyRunStatus;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyAccount;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyCase;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyRun;
import com.paytech.channel.connector.protocol.domain.ExternalApiLocks;
import com.paytech.channel.connector.protocol.domain.verify.VerifyCaseView;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyAccountRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyCaseRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyRunRepository;
import com.paytech.channel.connector.protocol.service.processor.request.ListIntegrationChildrenRequest;

import lombok.AllArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * V06 验证用例列表：分页读取用例，附最近一次运行与验证状态（沙箱验证技术方案第 5 节）。
 *
 * <p>验证状态由运行时记录的配置版本与当前版本比较得出；同一请求内每个标准 API 只加载一次当前配置。普通列表不开显式事务。
 */
@Component
@AllArgsConstructor
public class ListVerifyCasesProcessor
    extends AbstractProcessor<ListIntegrationChildrenRequest, PagingResponseBody<VerifyCaseView>> {

    private final VerifySupport verify;
    private final IntegrationChildSupport children;
    private final ChannelVerifyCaseRepository cases;
    private final ChannelVerifyRunRepository runs;
    private final ChannelVerifyAccountRepository accounts;

    @Override
    protected PagingResponseBody<VerifyCaseView> processRequest(ListIntegrationChildrenRequest request) {
        var integration = children.channel(request.getIntegrationCode());
        long channelId = integration.getChannelId();
        var paging = request.getPaging();
        List<ChannelVerifyCase> rows = cases.list(channelId, request.getKeyword(), paging.offset(), paging.size());
        long total = cases.count(channelId, request.getKeyword());
        var caseIds = rows.stream().map(ChannelVerifyCase::getVerifyCaseId).toList();
        Map<Long, ChannelVerifyRun> latest = runs.latestByCases(caseIds);
        Map<Long, ChannelVerifyRun> finished = runs.latestFinishedByCases(caseIds);
        Map<Long, ChannelVerifyAccount> accountById = accounts.allByChannel(channelId).stream()
            .collect(Collectors.toMap(ChannelVerifyAccount::getVerifyAccountId, Function.identity()));
        Map<String, JsonNode> current = new HashMap<>();
        var items = rows.stream().map(row -> {
            ChannelVerifyAccount account = accountById.get(row.getVerifyAccountId());
            ChannelVerifyRun done = finished.get(row.getVerifyCaseId());
            boolean stale = done != null && done.getRunStatus() == VerifyRunStatus.PASSED
                && verify.stale(done, row, account, integration.getChannelCode(), current);
            return verify.caseView(row, account == null ? null : account.getAccountCode(),
                VerifySupport.status(latest.get(row.getVerifyCaseId()), done, stale), latest.get(row.getVerifyCaseId()));
        }).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }
}
