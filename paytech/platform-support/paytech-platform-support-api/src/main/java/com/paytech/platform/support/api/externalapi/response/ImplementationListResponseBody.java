package com.paytech.platform.support.api.externalapi.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 接入方全部渠道标准 API 实现，不分页。 */
public class ImplementationListResponseBody {

    /**
     * 实现列表
     * <li>按标准 API 编码排序。</li>
     */
    private final List<ImplementationSummary> items;

    /** 按 JSON 属性绑定，保留字段的可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ImplementationListResponseBody(
        @JsonProperty("items") List<ImplementationSummary> items) {
        this.items = items == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(items));
    }

    /** 实现列表 */
    public List<ImplementationSummary> getItems() {
        return items;
    }
}
