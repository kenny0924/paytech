package com.paytech.client.contract.api.workspace;

import java.util.List;

import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

import com.paytech.client.contract.api.workspace.response.ClientSummariesResponseBody;

/** Client Console 工作区展示所需的 CCT 已批准客户摘要；只供 Client BFF 私网调用。 */
public interface ClientWorkspaceApi {
    /**
     * 按 Client ID 批量读取已批准客户摘要
     * <li>只返回存在 APPROVED 申请的 Client；其余 ID 静默省略，不区分不存在或未批准。</li>
     * <li>clientIds 由 Client BFF 从 Platform 当前账号的成员列表取得，CCT 不判断成员关系。</li>
     * <li>不返回证件号、地址或联系方式。</li>
     *
     * @param caller 私网调用方，必须为 client-console-bff
     * @param accountId 当前 CUSTOMER 账号
     * @param sessionId 当前 CLIENT 会话（账号或租户上下文）
     * @param clientIds 1—50 个规范十进制 Client ID，不得重复
     * @return 按 Client ID 升序的摘要
     */
    @GetExchange("/client-workspace/v1/client-summaries")
    ClientSummariesResponseBody summaries(@RequestHeader("X-Caller-Service") String caller,
        @RequestHeader("X-Gateway-Actor-Subject") String accountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @RequestParam("clientId") List<String> clientIds);
}
