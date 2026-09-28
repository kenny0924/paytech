package com.paytech.risk.api.decision.response;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonInclude;

import com.paytech.risk.api.decision.RiskVariableModels.*;

/** 普通查询返回分页；codes 批取时 page/size/total 为空且返回 missingCodes。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VariableListResponseBody {

    /** 本次查询返回的有界条目。 */
    private List<VariableSummary> items;

    /** 从 1 开始的页码；编码批取时不返回。 */
    private Integer page;

    /** 每页条数；编码批取时不返回。 */
    private Integer size;

    /** 相同筛选条件总数；编码批取时不返回。 */
    private Long total;

    /** 编码批取未找到的编码；普通分页不返回。 */
    private List<String> missingCodes;

    /** 当前值读取时间，不参与配置内容 Hash。 */
    private String valuesAsOf;

    /** JSON 反序列化构造。 */
    public VariableListResponseBody() { }

    /** 按完整合同构造。 */
    public VariableListResponseBody(List<VariableSummary> items, Integer page, Integer size, Long total, List<String> missingCodes, String valuesAsOf) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.total = total;
        this.missingCodes = missingCodes;
        this.valuesAsOf = valuesAsOf;
    }

    /** 读取本次查询返回的有界条目。 */
    public List<VariableSummary> getItems() { return items; }

    /** 设置本次查询返回的有界条目。 */
    public void setItems(List<VariableSummary> items) { this.items = items; }

    /** 读取从 1 开始的页码。 */
    public Integer getPage() { return page; }

    /** 设置从 1 开始的页码。 */
    public void setPage(Integer page) { this.page = page; }

    /** 读取每页条数。 */
    public Integer getSize() { return size; }

    /** 设置每页条数。 */
    public void setSize(Integer size) { this.size = size; }

    /** 读取相同筛选条件总数。 */
    public Long getTotal() { return total; }

    /** 设置相同筛选条件总数。 */
    public void setTotal(Long total) { this.total = total; }

    /** 读取编码批取未找到的编码。 */
    public List<String> getMissingCodes() { return missingCodes; }

    /** 设置编码批取未找到的编码。 */
    public void setMissingCodes(List<String> missingCodes) { this.missingCodes = missingCodes; }

    /** 读取当前值读取时间，不参与配置内容 Hash。 */
    public String getValuesAsOf() { return valuesAsOf; }

    /** 设置当前值读取时间，不参与配置内容 Hash。 */
    public void setValuesAsOf(String valuesAsOf) { this.valuesAsOf = valuesAsOf; }
}
