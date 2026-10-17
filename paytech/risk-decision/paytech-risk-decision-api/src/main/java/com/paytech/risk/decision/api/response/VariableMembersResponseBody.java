package com.paytech.risk.decision.api.response;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonInclude;

import com.paytech.risk.decision.api.RiskVariableModels.*;

/** 成员与根版本属于同一读取快照。 */
public class VariableMembersResponseBody {

    /** 永久唯一变量编码。 */
    private String variableCode;

    /** 根 CAS 版本，以正整数字符串表示。 */
    private String version;

    /** 本次查询返回的有界条目。 */
    private List<OrderedMember> items;

    /** 从 1 开始的页码；编码批取时不返回。 */
    private int page;

    /** 每页条数；编码批取时不返回。 */
    private int size;

    /** 相同筛选条件总数；编码批取时不返回。 */
    private long total;

    /** 当前值读取时间，不参与配置内容 Hash。 */
    private String valuesAsOf;

    /** JSON 反序列化构造。 */
    public VariableMembersResponseBody() { }

    /** 按完整合同构造。 */
    public VariableMembersResponseBody(String variableCode, String version, List<OrderedMember> items, int page, int size, long total, String valuesAsOf) {
        this.variableCode = variableCode;
        this.version = version;
        this.items = items;
        this.page = page;
        this.size = size;
        this.total = total;
        this.valuesAsOf = valuesAsOf;
    }

    /** 读取永久唯一变量编码。 */
    public String getVariableCode() { return variableCode; }

    /** 设置永久唯一变量编码。 */
    public void setVariableCode(String variableCode) { this.variableCode = variableCode; }

    /** 读取根 CAS 版本，以正整数字符串表示。 */
    public String getVersion() { return version; }

    /** 设置根 CAS 版本，以正整数字符串表示。 */
    public void setVersion(String version) { this.version = version; }

    /** 读取本次查询返回的有界条目。 */
    public List<OrderedMember> getItems() { return items; }

    /** 设置本次查询返回的有界条目。 */
    public void setItems(List<OrderedMember> items) { this.items = items; }

    /** 读取从 1 开始的页码。 */
    public int getPage() { return page; }

    /** 设置从 1 开始的页码。 */
    public void setPage(int page) { this.page = page; }

    /** 读取每页条数。 */
    public int getSize() { return size; }

    /** 设置每页条数。 */
    public void setSize(int size) { this.size = size; }

    /** 读取相同筛选条件总数。 */
    public long getTotal() { return total; }

    /** 设置相同筛选条件总数。 */
    public void setTotal(long total) { this.total = total; }

    /** 读取当前值读取时间，不参与配置内容 Hash。 */
    public String getValuesAsOf() { return valuesAsOf; }

    /** 设置当前值读取时间，不参与配置内容 Hash。 */
    public void setValuesAsOf(String valuesAsOf) { this.valuesAsOf = valuesAsOf; }
}
