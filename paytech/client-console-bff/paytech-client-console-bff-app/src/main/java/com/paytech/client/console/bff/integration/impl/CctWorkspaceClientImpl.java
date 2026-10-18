package com.paytech.client.console.bff.integration.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.client.console.bff.integration.CallerServiceCodes;
import com.paytech.client.console.bff.integration.CctWorkspaceClient;
import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.client.contract.api.workspace.ClientWorkspaceApi;
import com.paytech.client.contract.api.workspace.response.ClientSummaryResponseBody;
import com.paytech.foundation.common.error.ServiceUnavailableException;

import lombok.AllArgsConstructor;

/** 调用 CCT 工作区摘要接口，并核对回执只包含所请求且不重复的 Client。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "client.onboarding-enabled", havingValue = "true")
public class CctWorkspaceClientImpl implements CctWorkspaceClient {
    private final ClientWorkspaceApi clients;

    /** 按去重后的 Client ID 读取摘要并核对回执范围。 */
    @Override
    public Map<String, ClientSummaryResponseBody> summaries(ClientActor actor, List<String> clientIds) {
        if (clientIds.isEmpty()) {
            return Map.of();
        }
        var ids = clientIds.stream().distinct().toList();
        var value = clients.summaries(CallerServiceCodes.CLIENT_CONSOLE_BFF, actor.getAccountId(), actor.getSessionId(), ids);
        if (value == null || value.getItems() == null) {
            throw new ServiceUnavailableException();
        }
        Set<String> requested = Set.copyOf(ids);
        var result = new HashMap<String, ClientSummaryResponseBody>();
        for (var summary : value.getItems()) {
            if (summary == null || !requested.contains(summary.getClientId()) || result.put(summary.getClientId(), summary) != null) {
                throw new ServiceUnavailableException();
            }
        }
        return result;
    }
}
