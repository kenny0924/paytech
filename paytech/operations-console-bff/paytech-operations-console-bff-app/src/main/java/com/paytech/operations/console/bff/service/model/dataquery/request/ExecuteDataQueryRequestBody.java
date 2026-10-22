package com.paytech.operations.console.bff.service.model.dataquery.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.JsonNode;

/** 按模板编码执行查询：参数与分页；模板正文、约束与数据源取当前 ACTIVE 版本。 */
@Getter
@Setter
public class ExecuteDataQueryRequestBody {

    /**
     * 调用方参数
     * <li>JSON 对象，键为参数名；未声明的参数与以 __ 开头的系统参数直接拒绝。缺省为空对象。</li>
     * <li>校验失败返回 422 VALIDATION_ERROR，errors[].field 形如 params.conditions[2].minAmount。</li>
     */
    private JsonNode params;

    /**
     * 页码
     * <li>只用于 OFFSET，从 1 开始，缺省 1；(page-1)*size 不超过 data-query.limits.max-offset。</li>
     */
    @Positive
    private Integer page;

    /**
     * 页大小
     * <li>OFFSET、CURSOR 使用，1..模板 maxPageSize，缺省为 maxPageSize；NONE 忽略。</li>
     */
    @Positive
    private Integer size;

    /**
     * 是否返回总数
     * <li>只有 OFFSET 且模板 countAllowed=true 时允许为 true，否则返回 400。缺省 false。</li>
     */
    private boolean withTotal;

    /**
     * 游标
     * <li>只用于 CURSOR：上一页响应的 nextCursor 原样带回；首页不传。模板版本已变化时返回 400。</li>
     */
    @Size(max = 4096)
    private String cursor;
}
