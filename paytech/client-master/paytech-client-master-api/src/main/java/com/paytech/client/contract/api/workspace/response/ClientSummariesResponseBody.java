package com.paytech.client.contract.api.workspace.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 已批准客户摘要列表。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientSummariesResponseBody {
    /** 按 Client ID 升序；无可见 Client 时为空列表。 */
    private List<ClientSummaryResponseBody> items;
}
