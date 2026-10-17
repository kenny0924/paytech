package com.paytech.platform.support.api.tenantaccessmanagement.response;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 公共分页协议加计算时刻的传输投影。 */
public class EvaluatedPageResponseBody<T> {
    /** 当前页内容。 */
    private final List<T> items;

    /** 页码。 */
    private final int page;

    /** 分页大小。 */
    private final int size;

    /** 真实总数。 */
    private final long total;

    /** 同一查询的 UTC 计算时刻。 */
    private final Instant evaluatedAt;
    /** 按明确字段合同构造对象。 */
    @JsonCreator
    public EvaluatedPageResponseBody(@JsonProperty(value = "items") List<T> items,
        @JsonProperty(value = "page", required = true) int page,
        @JsonProperty(value = "size", required = true) int size,
        @JsonProperty(value = "total", required = true) long total,
        @JsonProperty(value = "evaluatedAt") Instant evaluatedAt) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.total = total;
        this.evaluatedAt = evaluatedAt;
    }

    /** 当前页内容。 */
    public List<T> getItems() { return items; }

    /** 页码。 */
    public int getPage() { return page; }

    /** 分页大小。 */
    public int getSize() { return size; }

    /** 真实总数。 */
    public long getTotal() { return total; }

    /** 同一查询的 UTC 计算时刻。 */
    public Instant getEvaluatedAt() { return evaluatedAt; }

    /** 按字段比较，保持原传输对象的值语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EvaluatedPageResponseBody<?> value)) return false;
        return Objects.equals(items, value.items)
            && Objects.equals(page, value.page)
            && Objects.equals(size, value.size)
            && Objects.equals(total, value.total)
            && Objects.equals(evaluatedAt, value.evaluatedAt);
    }

    @Override
    public int hashCode() { return Objects.hash(items, page, size, total, evaluatedAt); }
}
