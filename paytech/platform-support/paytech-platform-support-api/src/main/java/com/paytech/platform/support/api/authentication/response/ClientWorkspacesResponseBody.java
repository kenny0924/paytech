package com.paytech.platform.support.api.authentication.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 当前账号的 CLIENT 工作区列表；不含 PENDING 租户，按租户编号升序。 */
public final class ClientWorkspacesResponseBody {
    /** 工作区；最多 50 项。 */
    private final List<ClientWorkspaceResponseBody> items;

    /** 绑定协议字段并复制列表。 */
    @JsonCreator
    public ClientWorkspacesResponseBody(@JsonProperty("items") List<ClientWorkspaceResponseBody> items) {
        this.items = List.copyOf(items);
    }

    /** 返回不可修改的工作区列表。 */
    public List<ClientWorkspaceResponseBody> getItems() { return items; }
}
