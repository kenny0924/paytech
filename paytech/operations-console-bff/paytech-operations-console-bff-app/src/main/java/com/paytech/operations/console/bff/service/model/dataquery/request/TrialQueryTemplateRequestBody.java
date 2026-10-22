package com.paytech.operations.console.bff.service.model.dataquery.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.JsonNode;

/** 试运行草稿：内容来自请求，不保存；与执行共用参数、只读校验、分页与结果处理，游标版本号固定为 0。 */
@Getter
@Setter
public class TrialQueryTemplateRequestBody {

    /**
     * 数据源 ID
     * <li>规范正整数字符串；保存与校验时数据源必须为 ACTIVE。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "dataSourceId 必须为规范正整数字符串")
    private String dataSourceId;

    /**
     * 模板正文
     * <li>统一模板语法；UTF-8 不超过 data-query.limits.max-body-length（默认 64 KB）。</li>
     */
    @NotBlank
    @Size(max = 65536)
    private String body;

    /**
     * 参数声明（parameters_json）
     * <li>JSON 数组，每项 {name,type,elementType,required,default,maxLength,min,max,allowedValues,maxSize,choices,fields}；
     * 缺省为空数组。结构不合法时问题的 field 形如 parameters[2].choices。</li>
     */
    private JsonNode parameters;

    /** 出参列声明（columns_json），可选，最多 200 项。 */
    @Size(max = 200)
    private List<@Valid @NotNull QueryTemplateColumn> columns;

    /** 约束（constraints_json）。 */
    @NotNull
    @Valid
    private QueryTemplateConstraints constraints;

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
