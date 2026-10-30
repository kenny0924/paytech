package com.paytech.risk.decision.api.control.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Client 的档位变更历史，申请时间倒序，最多 100 条。 */
@Getter
@Setter
public class ClientTierChangeListResponseBody {

    /** 变更记录。 */
    private List<ClientTierChangeResponseBody> items;
}
