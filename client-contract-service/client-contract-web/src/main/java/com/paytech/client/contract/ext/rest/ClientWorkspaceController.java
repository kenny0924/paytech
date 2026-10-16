package com.paytech.client.contract.ext.rest;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.APPLICATION_NOT_FOUND;
import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.CLIENT_SESSION_INVALID;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.HashSet;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.contract.api.workspace.ClientWorkspaceApi;
import com.paytech.client.contract.api.workspace.response.ClientSummariesResponseBody;
import com.paytech.client.contract.api.workspace.response.ClientSummaryResponseBody;
import com.paytech.client.contract.core.dal.dataobject.ClientWorkspaceSummary;
import com.paytech.client.contract.domain.onboarding.ClientDraftCommand;
import com.paytech.client.contract.service.processor.ClientWorkspaceProcessor;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;

import lombok.AllArgsConstructor;

/** 工作区摘要私网协议：只接受 Client BFF，校验 ID 列表后委托只读查询。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(prefix = "onboarding", name = "draft-enabled", havingValue = "true")
public class ClientWorkspaceController implements ClientWorkspaceApi {
    private static final int MAX_CLIENT_IDS = 50;
    private static final DateTimeFormatter MICROS = new DateTimeFormatterBuilder().appendInstant(6).toFormatter();

    private final ClientWorkspaceProcessor processor;

    /** 非 Client BFF 调用按不存在隐藏；账号与会话坐标无效按会话失效处理。 */
    @Override
    public ClientSummariesResponseBody summaries(String caller, String account, String session, List<String> clientIds) {
        if (!"client-console-bff".equals(caller)) {
            throw new FailException(APPLICATION_NOT_FOUND);
        }
        long accountId;
        long sessionId;
        try {
            accountId = ClientDraftCommand.id(account);
            sessionId = ClientDraftCommand.id(session);
        } catch (FailException invalid) {
            throw new FailException(CLIENT_SESSION_INVALID);
        }
        var items = processor.summaries(accountId, sessionId, clientIds(clientIds)).stream().map(this::toBody).toList();
        return new ClientSummariesResponseBody(items);
    }

    /** 1—50 个规范十进制、不重复的 Client ID。 */
    List<Long> clientIds(List<String> values) {
        if (values == null || values.isEmpty() || values.size() > MAX_CLIENT_IDS) {
            throw new InvalidParameterException("clientId 数量必须为 1 到 50 个");
        }
        var seen = new HashSet<Long>();
        for (String value : values) {
            long id;
            try {
                id = ClientDraftCommand.id(value);
            } catch (FailException invalid) {
                throw new InvalidParameterException("clientId 必须为规范正整数");
            }
            if (!seen.add(id)) {
                throw new InvalidParameterException("clientId 不能重复");
            }
        }
        return values.stream().map(Long::parseLong).toList();
    }

    /** 只投影展示字段。 */
    ClientSummaryResponseBody toBody(ClientWorkspaceSummary summary) {
        return new ClientSummaryResponseBody(Long.toString(summary.getClientId()), summary.getClientNo(),
            summary.getClientType().getCode(), summary.getClientStatus().getCode(), summary.displayName(), summary.country(),
            summary.getBusinessModelCode(), summary.getApprovedAt() == null ? null : MICROS.format(summary.getApprovedAt()));
    }
}
