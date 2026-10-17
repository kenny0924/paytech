package com.paytech.platform.support.api.consolemanagement.response;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 有界完整树，客户端筛选时保留祖先。 */
public final class ConsoleResourcesResponseBody {
    /** 完整 Operations 资源列表。 */
    private final List<ConsoleResourceResponseBody> items;

    /** 绑定协议字段，保留原有 JSON 名称和校验约束。 */
    @JsonCreator
    public ConsoleResourcesResponseBody(@JsonProperty(value = "items") List<ConsoleResourceResponseBody> items) {
        this.items = items;
    }

    /** 完整 Operations 资源列表。 */
    public List<ConsoleResourceResponseBody> getItems() {
        return items;
    }
}
