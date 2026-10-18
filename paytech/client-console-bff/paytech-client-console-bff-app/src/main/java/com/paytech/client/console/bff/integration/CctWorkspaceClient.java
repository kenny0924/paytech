package com.paytech.client.console.bff.integration;

import java.util.List;
import java.util.Map;

import com.paytech.client.console.bff.service.model.ClientActor;
import com.paytech.client.contract.api.workspace.response.ClientSummaryResponseBody;

/** CCT 已批准 Client 摘要的出站端口。 */
public interface CctWorkspaceClient {

    /**
     * 读取一组 Client 的已批准摘要。
     *
     * @param actor 当前可信身份
     * @param clientIds 待读取的 Client ID，可重复
     * @return 按 Client ID 索引的摘要；CCT 只返回本账号已批准的 Client，未返回的不视为错误。
     *     回执包含未请求或重复的 Client 时按依赖不可用抛出；请求为空时不调用 CCT
     */
    Map<String, ClientSummaryResponseBody> summaries(ClientActor actor, List<String> clientIds);
}
