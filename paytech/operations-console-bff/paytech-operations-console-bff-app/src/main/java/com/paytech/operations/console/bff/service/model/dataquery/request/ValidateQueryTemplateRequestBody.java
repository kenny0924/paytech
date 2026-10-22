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

/** 编辑器实时校验草稿，不保存；按保存时的同一套规则给出问题列表。 */
@Getter
@Setter
public class ValidateQueryTemplateRequestBody {

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
}
